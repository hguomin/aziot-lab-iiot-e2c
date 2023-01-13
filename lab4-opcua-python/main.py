import json
import logging
import asyncio
from asyncua import Client, ua, Node

#aziot
import uuid
from azure.iot.device.aio import IoTHubDeviceClient
from azure.iot.device import Message


# https://github.com/Azure/azure-iot-sdk-python/tree/main/samples

ua_server_url = "opc.tcp://localhost:53530/OPCUA/SimulationServer"
az_iothub_device_connstr = "HostName=gm-iot-hub.azure-devices.cn;DeviceId=gmdev1;SharedAccessKey=lxrscOIukFS8K9Im/AKp2yAu2fwUl7CYkPiX14TGEtU="
logging.basicConfig(level=logging.INFO)
_logger = logging.getLogger("AzIoT OPCUA Edge")

class UaSubscriptionHandler:
    def __init__(self, device_client: IoTHubDeviceClient) -> None:
        self._device_client = device_client
    """
    Subscription Handler. To receive events from server for a subscription
    """
    async def datachange_notification(self, node: Node, val, data):
        """
        called for every datachange notification from server
        """
        _logger.info("datachange_notification %r %s", node, val)

        msg_data = {}
        msg_data[node.nodeid.to_string()] = val
        msg = Message(json.dumps(msg_data))
        
        msg.message_id = uuid.uuid4()
        msg.custom_properties["data_src"] = "prosys"
        msg.content_encoding = "utf-8"
        msg.content_type = "application/json"

        await self._device_client.send_message(msg)

    def event_notification(self, event: ua.EventNotificationList):
        """
        called for every event notification from server
        """
        _logger.info("event_notification %r", event)

    def status_change_notification(self, status: ua.StatusChangeNotification):
        """
        called for every status change notification from server
        """
        _logger.info("status_notification %s", status)

async def main():
    device_client = IoTHubDeviceClient.create_from_connection_string(az_iothub_device_connstr)
    await device_client.connect()

    sub_handler = UaSubscriptionHandler(device_client)

    while True:
        client = Client(url=ua_server_url)
        try:
            async with client:
                _logger.info("Connected to server: " + ua_server_url)

                nodes = [client.get_node("ns=3;i=1001")]
                sub = await client.create_subscription(500, sub_handler)
                await sub.subscribe_data_change(nodes=nodes)

                while True:
                    # Throws a exception if connection is lost
                    await client.check_connection()  
                    await asyncio.sleep(2)

                    node = client.get_node('ns=3;i=1004')
                    value = await node.read_value()
                    _logger.info("%s", value)
                    
        except Exception as err:
            _logger.warning("Connection lost, let's reconnect in one second...")
            await asyncio.sleep(1)

if __name__=="__main__":
    asyncio.run(main())