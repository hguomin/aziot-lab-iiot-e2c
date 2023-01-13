# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE file in the project root for
# full license information.

import os
import asyncio
import sys
import signal
import threading
from azure.iot.device.aio import IoTHubDeviceClient, IoTHubModuleClient
from azure.iot.device import Message, MethodRequest, MethodResponse

import json
import logging
import uuid
from asyncua import Client, ua, Node

class IoTClient:
    def __init__(self, client, logger: logging.Logger) -> None:
        self._client = client
        self._logger = logger

        self._client.on_method_request_received = self.method_request_handler
        self._client.on_twin_desired_properties_patch_received  = self.twin_patch_handler

    async def send_message(self, msg_data):
        msg = Message(data=json.dumps(msg_data), message_id=uuid.uuid4, content_encoding="utf-8", content_type="application/json")
        await self._client.send_message(msg)

    async def method_request_handler(self, method_request: MethodRequest):
        if method_request.name == "start":
            payload = {"result": True, "data": "some data"}
            status = 200
            self._logger.info("executed method: start")
        else:
            payload = {"result": False, "data": "unknown method"}
            status = 400
            self._logger.warn("executed unknown method: " + method_request.name)

        response = MethodResponse.create_from_method_request(method_request, 200, payload)
        await self._client.send_method_response(response)

    async def twin_patch_handler(self, twin_patch):
        self._logger.info("the data in the desired properties patch was: {}".format(twin_patch))


class UaSubscriptionHandler:
    def __init__(self, iot_client: IoTClient) -> None:
        self._iot_client = iot_client
    """
    Subscription Handler. To receive events from server for a subscription
    """
    async def datachange_notification(self, node: Node, val, data):
        """
        called for every datachange notification from server
        """
        logger.info("datachange_notification %r %s", node, val)

        msg_data = {}
        msg_data[node.nodeid.to_string()] = val
        
        await self._iot_client.send_message(msg_data)

    def event_notification(self, event: ua.EventNotificationList):
        """
        called for every event notification from server
        """
        logger.info("event_notification %r", event)

    def status_change_notification(self, status: ua.StatusChangeNotification):
        """
        called for every status change notification from server
        """
        logger.info("status_notification %s", status)

# Event indicating client stop
stop_event = threading.Event()

run_in_iotedge = len(os.getenv("IOTEDGE_IOTHUBHOSTNAME")) > 0 and len(os.getenv("IOTEDGE_MODULEGENERATIONID")) > 0 and len(os.getenv("IOTEDGE_WORKLOADURI")) > 0 and len(os.getenv("IOTEDGE_MODULEID")) > 0
run_in_iotedge = not run_in_iotedge

ua_server_url = "opc.tcp://10.2.0.4:53530/OPCUA/SimulationServer"
az_iothub_device_connstr = "HostName=gm-iot-hub.azure-devices.cn;DeviceId=gmdev1;SharedAccessKey=lxrscOIukFS8K9Im/AKp2yAu2fwUl7CYkPiX14TGEtU=" #os.getenv("IOTHUB_DEVICE_CONNECTION_STRING")
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("AzIoT OPCUA Edge")

def create_iothub_client():
    device_client = IoTHubDeviceClient.create_from_connection_string(az_iothub_device_connstr)
    device_client.connect()
    return device_client

def create_iotedge_client():
    client = IoTHubModuleClient.create_from_edge_environment()

    # Define function for handling received messages
    async def receive_message_handler(message):
        # NOTE: This function only handles messages sent to "input1".
        # Messages sent to other inputs, or to the default, will be discarded
        if message.input_name == "input1":
            print("the data in the message received on input1 was ")
            print(message.data)
            print("custom properties are")
            print(message.custom_properties)
            print("forwarding mesage to output1")
            await client.send_message_to_output(message, "output1")

    try:
        # Set handler on the client
        client.on_message_received = receive_message_handler

    except:
        # Cleanup if failure occurs
        client.shutdown()
        raise

    return client


async def run_sample(client):
    # Customize this coroutine to do whatever tasks the module initiates
    # e.g. sending messages
    while True:
        await asyncio.sleep(1000)

async def run_apps(iot_client):
    sub_handler = UaSubscriptionHandler(iot_client)
    while True:
        client = Client(url=ua_server_url)
        try:
            async with client:
                logger.info("Connected to server: " + ua_server_url)

                nodes = [client.get_node("ns=3;i=1001")]
                sub = await client.create_subscription(500, sub_handler)
                await sub.subscribe_data_change(nodes=nodes)

                while True:
                    # Throws a exception if connection is lost
                    await client.check_connection()  
                    await asyncio.sleep(2)

                    node = client.get_node('ns=3;i=1004')
                    value = await node.read_value()
                    logger.info("%s", value)
                    
        except Exception as err:
            logger.warning("Connection lost, let's reconnect in one second...")
            await asyncio.sleep(1)

def main():
    if not sys.version >= "3.5.3":
        raise Exception( "The sample requires python 3.5.3+. Current version of Python: %s" % sys.version )
    print ( "IoT Hub Client for Python" )

    # NOTE: Client is implicitly connected due to the handler being set on it

    client = None
    if(run_in_iotedge):
        client = IoTClient(create_iotedge_client(), logger)
    else:
        client = IoTClient(create_iothub_client(), logger)

    # Define a handler to cleanup when module is is terminated by Edge
    def module_termination_handler(signal, frame):
        print ("IoTHubClient sample stopped by Edge")
        stop_event.set()

    # Set the Edge termination handler
    signal.signal(signal.SIGTERM, module_termination_handler)

    # Run the sample
    loop = asyncio.get_event_loop()
    try:
        loop.run_until_complete(run_apps(client))
    except Exception as e:
        print("Unexpected error %s " % e)
        raise
    finally:
        print("Shutting down IoT Hub Client...")
        loop.run_until_complete(client.shutdown())
        loop.close()


if __name__ == "__main__":
    main()
