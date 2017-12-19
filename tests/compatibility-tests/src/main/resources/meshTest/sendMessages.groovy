package meshTest

import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*

/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

// starts an artemis server
String serverType = arg[0];
String clientType = arg[1];
String operation = arg[2];


String queueName = "queue";

int LARGE_MESSAGE_SIZE = 10 * 1024;

String propertyLargeMessage = "JMS_AMQ_InputStream";
HDR_DUPLICATE_DETECTION_ID = "_AMQ_DUPL_ID";

if (clientType.startsWith("HORNETQ")) {
    HDR_DUPLICATE_DETECTION_ID = "_HQ_DUPL_ID";
    propertyLargeMessage = "JMS_HQ_InputStream"
}

BYTES_BODY = new byte[3];
BYTES_BODY[0] = (byte) 0x77;
BYTES_BODY[1] = (byte) 0x77;
BYTES_BODY[2] = (byte) 0x77;

String textBody = "a rapadura e doce mas nao e mole nao";


println("serverType " + serverType);

if (clientType.startsWith("ARTEMIS")) {
    // Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
    GroovyRun.evaluate("clients/artemisClient.groovy", "serverArg", serverType);
} else {
    // Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
    GroovyRun.evaluate("clients/hornetqClient.groovy", "serverArg");
}


Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
Queue queue = session.createQueue(queueName);

if (operation.equals("sendAckMessages")) {
    MessageProducer producer = session.createProducer(queue);
    producer.setDeliveryMode(DeliveryMode.PERSISTENT);

    System.out.println("Sending messages");

    TextMessage message = session.createTextMessage(textBody);
    message.setStringProperty(HDR_DUPLICATE_DETECTION_ID, "some-duplicate");
    message.setStringProperty("prop", "test");
    producer.send(message);

    BytesMessage bytesMessage = session.createBytesMessage();
    bytesMessage.writeBytes(BYTES_BODY);
    producer.send(bytesMessage);


    for (int i = 0; i < 10; i++) {
        BytesMessage m = session.createBytesMessage();
        m.setIntProperty("count", i);

        m.setObjectProperty(propertyLargeMessage, createFakeLargeStream(LARGE_MESSAGE_SIZE));

        producer.send(m);
    }

    producer.send(session.createObjectMessage("rapadura"));

    MapMessage mapMessage = session.createMapMessage();
    mapMessage.setString("prop", "rapadura")
    producer.send(mapMessage);

    StreamMessage streamMessage = session.createStreamMessage();
    streamMessage.writeString("rapadura");
    streamMessage.writeString("doce");
    streamMessage.writeInt(33);
    producer.send(streamMessage);

    Message plain = session.createMessage();
    plain.setStringProperty("plain", "doce");
    producer.send(plain);

    session.commit();

    connection.close();
    System.out.println("Message sent");
} else if (operation.equals("receiveMessages")) {
    MessageConsumer consumer = session.createConsumer(queue);
    connection.start();

    System.out.println("Receiving messages");

    TextMessage message = (TextMessage) consumer.receive(5000);
    GroovyRun.assertNotNull(message);
    GroovyRun.assertEquals(textBody, message.getText());
    GroovyRun.assertEquals("test", message.getStringProperty("prop"));
    GroovyRun.assertEquals("some-duplicate", message.getStringProperty(HDR_DUPLICATE_DETECTION_ID));

    BytesMessage bm = (BytesMessage) consumer.receive(5000);
    GroovyRun.assertNotNull(bm);

    GroovyRun.assertEquals(3L, bm.getBodyLength());

    byte[] body = new byte[3];
    bm.readBytes(body);

    GroovyRun.assertEquals(BYTES_BODY, body);

    for (int m = 0; m < 10; m++) {
        BytesMessage rm = (BytesMessage) consumer.receive(10000);
        GroovyRun.assertNotNull(rm);
        GroovyRun.assertEquals(m, rm.getIntProperty("count"));

        byte[] data = new byte[1024];

        System.out.println("Message = " + rm);

        for (int i = 0; i < LARGE_MESSAGE_SIZE; i += 1024) {
            int numberOfBytes = rm.readBytes(data);
            GroovyRun.assertEquals(1024, numberOfBytes);
            for (int j = 0; j < 1024; j++) {
                GroovyRun.assertEquals(GroovyRun.getSamplebyte(i + j), data[j]);
            }
        }
    }


    ObjectMessage obj = consumer.receive(5000);
    GroovyRun.assertNotNull(obj);
    GroovyRun.assertEquals("rapadura", obj.getObject().toString());

    MapMessage mapMessage = consumer.receive(5000);
    GroovyRun.assertNotNull(mapMessage);
    GroovyRun.assertEquals("rapadura", mapMessage.getString("prop"));

    StreamMessage streamMessage = consumer.receive(5000);
    GroovyRun.assertNotNull(streamMessage);
    GroovyRun.assertEquals("rapadura", streamMessage.readString());
    GroovyRun.assertEquals("doce", streamMessage.readString());
    GroovyRun.assertTrue(streamMessage.readInt() == 33);

    Message plain = consumer.receive(5000);
    GroovyRun.assertNotNull(plain);
    GroovyRun.assertEquals("doce", plain.getStringProperty("plain"));

    session.commit();
    connection.close();
    System.out.println("Message received");
} else {
    throw new RuntimeException("Invalid operation " + operation);
}


// Creates a Fake LargeStream without using a real file
InputStream createFakeLargeStream(final long size) throws Exception {
    return new InputStream() {
        private long count;

        private boolean closed = false;

        @Override
        void close() throws IOException {
            super.close();
            closed = true;
        }

        @Override
        int read() throws IOException {
            if (closed) {
                throw new IOException("Stream was closed");
            }
            if (count++ < size) {
                return GroovyRun.getSamplebyte(count - 1);
            }
            else {
                return -1;
            }
        }

    };

}



