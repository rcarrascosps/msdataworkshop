package oracle.db.microservices;

import oracle.AQ.*;
import oracle.jms.*;

import javax.jms.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static oracle.db.microservices.ATPAQAdminResource.*;

class PropagationSetup {
    String GET_OBJECT_CWALLETSSO_DATA_PUMP_DIR = "BEGIN " +
            "DBMS_CLOUD.GET_OBJECT(" +
            "object_uri => '" + cwalletobjecturi + "', " +
            "directory_name => 'DATA_PUMP_DIR'); " +
            "END;";

    String DROP_CREDENTIAL_INVENTORYPDB_CRED_SQL = "BEGIN " +
            "DBMS_CLOUD.DROP_CREDENTIAL(" +
            "credential_name => 'INVENTORYPDB_CRED'" +
            ");" +
            "END;";

    String CREATE_CREDENTIAL_INVENTORYPDB_CRED_SQL = " BEGIN" +
            "             DBMS_CLOUD.CREATE_CREDENTIAL(" +
            "             credential_name => 'INVENTORYPDB_CRED'," +
            "             username => '" + inventoryuser + "'," +
            "             password => '" + inventorypw.trim() + "'" +
            "             );" +
            "            END;";

    String CREATE_DBLINK_ORDERTOINVENTORY_SQL = "BEGIN " +
            "DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(" +
            "db_link_name => '" + orderToInventoryLinkName + "'," +
            "hostname => '" + inventoryhostname + "'," +
            "port => '" + inventoryport + "'," +
            "service_name => '" + inventoryservice_name + "'," +
            "ssl_server_cert_dn => '" + inventoryssl_server_cert_dn + "'," +
            "credential_name => 'INVENTORYPDB_CRED'," +
            "directory_name => 'DATA_PUMP_DIR');" +
            "END;";

    String DROP_CREDENTIAL_ORDERPDB_CRED_SQL = "BEGIN " +
            "DBMS_CLOUD.DROP_CREDENTIAL(" +
            "credential_name => 'ORDERPDB_CRED'" +
            ");" +
            "END;";

    String CREATE_CREDENTIAL_ORDERPDB_CRED_SQL = " BEGIN" +
            "             DBMS_CLOUD.CREATE_CREDENTIAL(" +
            "             credential_name => 'ORDERPDB_CRED'," +
            "             username => '" + orderuser + "'," +
            "             password => '" + orderpw.trim() + "'" +
            "             );" +
            "            END;";

    String CREATE_DBLINK_INVENTORYTOORDER_SQL = "BEGIN " +
            "DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(" +
            "db_link_name => '" + inventoryToOrderLinkName + "'," +
            "hostname => '" + orderhostname + "'," +
            "port => '" + orderport + "'," +
            "service_name => '" + orderservice_name + "'," +
            "ssl_server_cert_dn => '" + orderssl_server_cert_dn + "'," +
            "credential_name => 'ORDERPDB_CRED'," +
            "directory_name => 'DATA_PUMP_DIR');" +
            "END;";

    String createInventoryTable(DataSource inventorypdbDataSource) throws SQLException {
        System.out.println("createInventoryTable and add items");
        String returnValue = "createInventoryTable and add items\n";
        try {
            Connection connection = inventorypdbDataSource.getConnection(inventoryuser, inventorypw);
            connection.createStatement().execute(
                    "create table inventory (inventoryid varchar(16), inventorylocation varchar(32), inventorycount integer)");
            returnValue += " table created, ";
            connection.createStatement().execute("insert into inventory values ('carrots', '1st Street', 0)");
            connection.createStatement().execute("insert into inventory values ('cucumbers', '2nd Street', 0)");
            connection.createStatement().execute("insert into inventory values ('tomatoes', '3rd Street', 0)");
            connection.createStatement().execute("insert into inventory values ('onions', '4th Street', 0)");
            returnValue += " table populated with veggie types, ";
            returnValue += "success";
        } catch (SQLException ex) {
            ex.printStackTrace();
            returnValue += ex.toString();
        }
        return returnValue;
    }

    String createUsers(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) throws SQLException {
        String returnValue = "";
        try {
            returnValue += createAQUser(orderpdbDataSource, orderuser, orderpw);
        } catch (SQLException ex) {
            ex.printStackTrace();
            returnValue += ex.toString();
        }
        try {
            returnValue += createAQUser(inventorypdbDataSource, inventoryuser, inventorypw);
        } catch (SQLException ex) {
            ex.printStackTrace();
            returnValue += ex.toString();
        }
        return returnValue;
    }

    Object createAQUser(DataSource ds, String queueOwner, String queueOwnerPW) throws SQLException {
        String outputString = "createAQUser queueOwner = [" + queueOwner + "]";
        System.out.println(outputString + "queueOwnerPW = [" + queueOwnerPW + "]");
        Connection connection = ds.getConnection();
        connection.createStatement().execute("grant pdb_dba to " + queueOwner + " identified by " + queueOwnerPW);
        connection.createStatement().execute("GRANT EXECUTE ON DBMS_CLOUD_ADMIN TO " + queueOwner);
        connection.createStatement().execute("GRANT EXECUTE ON DBMS_CLOUD TO " + queueOwner);
        connection.createStatement().execute("GRANT CREATE DATABASE LINK TO " + queueOwner);
        connection.createStatement().execute("grant unlimited tablespace to " + queueOwner);
        connection.createStatement().execute("grant connect, resource TO " + queueOwner);
        connection.createStatement().execute("grant aq_user_role TO " + queueOwner);
        connection.createStatement().execute("GRANT EXECUTE ON sys.dbms_aqadm TO " + queueOwner);
        connection.createStatement().execute("GRANT EXECUTE ON sys.dbms_aq TO " + queueOwner);
        connection.createStatement().execute("GRANT EXECUTE ON sys.dbms_aq TO " + queueOwner);
        //    sysDBAConnection.createStatement().execute("create table tracking (state number)");
        return outputString + " successful";
    }

    String createDBLinks(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) throws SQLException {
        System.out.println("createDBLinks...");
        // create link from order to inventory...
        createDBLink(orderpdbDataSource.getConnection(orderuser, orderpw),
                GET_OBJECT_CWALLETSSO_DATA_PUMP_DIR, DROP_CREDENTIAL_INVENTORYPDB_CRED_SQL,
                CREATE_CREDENTIAL_INVENTORYPDB_CRED_SQL, CREATE_DBLINK_ORDERTOINVENTORY_SQL, orderToInventoryLinkName);
        // create link from inventory to order...
        createDBLink(inventorypdbDataSource.getConnection(inventoryuser, inventorypw),
                GET_OBJECT_CWALLETSSO_DATA_PUMP_DIR, DROP_CREDENTIAL_ORDERPDB_CRED_SQL,
                CREATE_CREDENTIAL_ORDERPDB_CRED_SQL, CREATE_DBLINK_INVENTORYTOORDER_SQL, inventoryToOrderLinkName);
        String verifyDBLinksReply = verifyDBLinks(orderpdbDataSource, inventorypdbDataSource);
        return "DBLinks created and verified successfully";
    }

    private void createDBLink(Connection connection, String getobject, String dropcred,
                                String createcred, String createlink, String linkname) throws SQLException {
        System.out.println(" creating link:" + linkname);
        System.out.println("\n about to " + getobject);
        connection.createStatement().execute(getobject);
        try {
            System.out.println("\n GET_OBJECT cwalletobjecturi successful, about to (if exists_" + dropcred);
            connection.createStatement().execute(dropcred);
        } catch (SQLException ex) {
            System.out.println("SQLException from DROP_CREDENTIAL_INVENTORYPDB_CRED_SQL (likely expected) :" + ex);
        }
        System.out.println("\n  GET_OBJECT cwalletobjecturi successful, about to " + createcred);
        connection.createStatement().execute(createcred);
        System.out.println("\n CREATE_CREDENTIAL INVENTORYPDB_CRED successful, about to " + createlink);
        connection.createStatement().execute(createlink);
        System.out.println("\n CREATE_DATABASE_LINK " + linkname + " successful,");
    }

    String verifyDBLinks(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) throws SQLException {
        Connection orderconnection = orderpdbDataSource.getConnection(orderuser, orderpw);
        Connection inventoryconnection = inventorypdbDataSource.getConnection(inventoryuser, inventorypw);
        System.out.println("verifyDBLinks orderconnection:" + orderconnection +
                " inventoryconnection:" + inventoryconnection);
        orderconnection.createStatement().execute("create table templinktest (id varchar(32))");
        System.out.println("verifyDBLinks temp table created on order");
        inventoryconnection.createStatement().execute("create table templinktest (id varchar(32))");
        System.out.println("verifyDBLinks temp table created on inventory");
        // verify orderuser select on inventorypdb using link...
        orderconnection.createStatement().execute("select count(*) from inventoryuser.templinktest@" + orderToInventoryLinkName);
        System.out.println("verifyDBLinks select on inventoryuser.templinktest");
        // verify inventoryuser select on orderpdb using link  ...
        inventoryconnection.createStatement().execute("select count(*) from orderuser.templinktest@" + inventoryToOrderLinkName);
        System.out.println("verifyDBLinks select on orderuser.templinktest");
        orderconnection.createStatement().execute("drop table templinktest");
        inventoryconnection.createStatement().execute("drop table templinktest");
        return "success";
    }

    public String setup(DataSource orderpdbDataSource, DataSource inventorypdbDataSource,
                        boolean isSetupOrderToInventory, boolean isSetupInventoryToOrder) {
        String returnString = "in setup... " +
                "isSetupOrderToInventory:" + isSetupOrderToInventory + " isSetupInventoryToOrder:" + isSetupInventoryToOrder;
        //propagation of order queue from orderpdb to inventorypdb
        if (isSetupOrderToInventory) returnString += setup(orderpdbDataSource, inventorypdbDataSource,
                orderuser, orderpw, orderQueueName,
                orderQueueTableName, inventoryuser, inventorypw,
                orderToInventoryLinkName, false);
        //propagation of inventory queue from inventorypdb to orderpdb
        if (isSetupInventoryToOrder) returnString += setup(inventorypdbDataSource, orderpdbDataSource,
                inventoryuser, inventorypw, inventoryQueueName,
                inventoryQueueTableName, orderuser, orderpw,
                inventoryToOrderLinkName, false);
        return returnString;
    }

    public String testOrderToInventory(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) {
        String returnString = "in testOrderToInventory...";
        //propagation of order queue from orderpdb to inventorypdb
        returnString += setup(orderpdbDataSource, inventorypdbDataSource,
                orderuser, orderpw, orderQueueName,
                orderQueueTableName, inventoryuser, inventorypw,
                orderToInventoryLinkName, true);
        return returnString;
    }

    public String testInventoryToOrder(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) {
        String returnString = "in testInventoryToOrder...";
        //propagation of inventory queue from inventorypdb to orderpdb
        returnString += setup(inventorypdbDataSource, orderpdbDataSource,
                inventoryuser, inventorypw, inventoryQueueName,
                inventoryQueueTableName, orderuser, orderpw,
                inventoryToOrderLinkName, true);
        return returnString;
    }

    private String setup(
            DataSource sourcepdbDataSource, DataSource targetpdbDataSource, String sourcename, String sourcepw,
            String sourcequeuename, String sourcequeuetable, String targetuser, String targetpw,
            String linkName, boolean isTest) {
        String returnString = "sourcepdbDataSource = [" + sourcepdbDataSource + "], " +
                "targetpdbDataSource = [" + targetpdbDataSource + "], " +
                "sourcename = [" + sourcename + "], sourcepw = [" + sourcepw + "], " +
                "sourcequeuename = [" + sourcequeuename + "], " +
                "sourcequeuetable = [" + sourcequeuetable + "], targetuser = [" + targetuser + "], " +
                "targetpw = [" + targetpw + "], linkName = [" + linkName + "] isTest = " + isTest;
        System.out.println(returnString);
        try {
            TopicConnectionFactory tcfact = AQjmsFactory.getTopicConnectionFactory(sourcepdbDataSource);
            TopicConnection tconn = tcfact.createTopicConnection(sourcename, sourcepw);
            QueueConnectionFactory qcfact = AQjmsFactory.getQueueConnectionFactory(targetpdbDataSource);
            QueueConnection qconn = qcfact.createQueueConnection(targetuser, targetpw);
            TopicSession tsess = tconn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            System.out.println("setup source topicsession:" + tsess);
            QueueSession qsess = qconn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
            System.out.println("setup destination queuesession:" + qsess);
            tconn.start();
            qconn.start();
            if (!isTest) setupTopicAndQueue(tsess, qsess, sourcename, targetuser, sourcequeuename, sourcequeuetable);
            performJmsOperations(tsess, qsess, sourcename, targetuser, sourcequeuename, linkName, isTest);
            tsess.close();
            tconn.close();
            qsess.close();
            qconn.close();
            System.out.println("success");
            returnString += "\n ...success";
            return returnString;
        } catch (Exception ex) {
            System.out.println("Exception-1: " + ex);
            ex.printStackTrace();
            return returnString += "\n ..." + ex.toString();
        }
    }

    public static void setupTopicAndQueue(
            TopicSession topicSession, QueueSession queueSession,
            String topicuser, String queueuser,
            String name,
            String tableName) throws Exception {
        try {
            System.out.println("drop source queue table if it exists...");
            try {
                AQQueueTable qtable = ((AQjmsSession) topicSession).getQueueTable(topicuser, tableName);
                qtable.drop(true);
            } catch (Exception e) {
                System.out.println("Exception in dropping source (expected if it does not exist)" + e);
            }
            System.out.println("drop destination queue table if it exists...");
            try {
                AQQueueTable qtable = ((AQjmsSession) queueSession).getQueueTable(queueuser, tableName);
                qtable.drop(true);
            } catch (Exception e) {
                System.out.println("Exception in dropping destination (expected if it does not exist)" + e);
            }
            System.out.println("Creating Input Topic Table...");
            AQQueueTableProperty aqQueueTableProperty = new AQQueueTableProperty("SYS.AQ$_JMS_TEXT_MESSAGE");
            aqQueueTableProperty.setComment("input topic");
            aqQueueTableProperty.setMultiConsumer(true);
            aqQueueTableProperty.setCompatible("8.1");
            aqQueueTableProperty.setPayloadType("SYS.AQ$_JMS_TEXT_MESSAGE");
            AQQueueTable inputTopicTable = ((AQjmsSession) topicSession).createQueueTable(topicuser, tableName, aqQueueTableProperty);

            System.out.println("Creating Propagation Queue Table...");
            AQQueueTableProperty qtprop2 = new AQQueueTableProperty("SYS.AQ$_JMS_TEXT_MESSAGE");
            qtprop2.setComment("propagation queue");
            qtprop2.setPayloadType("SYS.AQ$_JMS_TEXT_MESSAGE");
            qtprop2.setMultiConsumer(false);
            qtprop2.setCompatible("8.1");
            AQQueueTable propagationQueueTable = ((AQjmsSession) queueSession).createQueueTable(queueuser, tableName, qtprop2);

            System.out.println("Creating Topic input_queue...");
            AQjmsDestinationProperty aqjmsDestinationProperty = new AQjmsDestinationProperty();
            aqjmsDestinationProperty.setComment("create topic ");
            Topic topic1 = ((AQjmsSession) topicSession).createTopic(inputTopicTable, name, aqjmsDestinationProperty);

            System.out.println("Creating queue prop_queue...");
            aqjmsDestinationProperty.setComment("create queue");
            Queue queue1 = ((AQjmsSession) queueSession).createQueue(propagationQueueTable, name, aqjmsDestinationProperty);

            ((AQjmsDestination) topic1).start(topicSession, true, true);
            ((AQjmsDestination) queue1).start(queueSession, true, true);
            System.out.println("Successfully setup topic and queue");
        } catch (Exception ex) {
            System.out.println("Error in setupTopic: " + ex);
            throw ex;
        }
    }


    public static void performJmsOperations(
            TopicSession topicSession, QueueSession queueSession,
            String sourcetopicuser, String destinationqueueuser,
            String name,
            String linkName, boolean isTest)
            throws Exception {
        try {
            System.out.println("Setup topic/source and queue/destination for propagation... isTest:" + isTest);
            Topic topic1 = ((AQjmsSession) topicSession).getTopic(sourcetopicuser, name);
            Queue queue = ((AQjmsSession) queueSession).getQueue(destinationqueueuser, name);
            System.out.println("Creating Topic Subscribers... queue:" + queue.getQueueName());
            AQjmsConsumer[] subs = new AQjmsConsumer[1];
            subs[0] = (AQjmsConsumer) queueSession.createConsumer(queue);
            if (!isTest)
                createRemoteSubAndSchedulePropagation(topicSession, destinationqueueuser, name, linkName, topic1, queue);
            sendMessages(topicSession, topic1);
            Thread.sleep(50000);
            receiveMessages(queueSession, subs);
        } catch (Exception e) {
            System.out.println("Error in performJmsOperations: " + e);
            throw e;
        }
    }

    private static void createRemoteSubAndSchedulePropagation(TopicSession topicSession, String destinationqueueuser, String name, String linkName, Topic topic1, Queue queue) throws JMSException, SQLException {
        System.out.println("_____________________________________________");
        System.out.println("performJmsOperations queue.getQueueName():" + queue.getQueueName());
        System.out.println("performJmsOperations name (prefixing " + destinationqueueuser + ". to this):" + name);
        System.out.println("_____________________________________________");
        AQjmsAgent agt = new AQjmsAgent("", destinationqueueuser + "." + name + "@" + linkName);
        ((AQjmsSession) topicSession).createRemoteSubscriber(topic1, agt, "JMSPriority = 2");
        ((AQjmsDestination) topic1).schedulePropagation(
                topicSession, linkName, null, null, null, new Double(0));
    }

    String enablePropagation(DataSource dataSource, String topicUser, String topicPassword, String topicName, String linkName) {
        System.out.println("schedulePropagation");
        TopicConnection tconn = null;
        try {
            tconn = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection(
                    topicUser, topicPassword);
            TopicSession tsess = tconn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            Topic topic1 = ((AQjmsSession) tsess).getTopic(topicUser, topicName);
            ((AQjmsDestination) topic1).enablePropagationSchedule(tsess, linkName);
        } catch (JMSException e) {
            e.printStackTrace();
            return e.toString();
        }
        return "success";
    }

    private static void sendMessages(TopicSession topicSession, Topic topic) throws JMSException {
        System.out.println("Publish test messages...");
        TextMessage objmsg = topicSession.createTextMessage();
        TopicPublisher publisher = topicSession.createPublisher(topic);
        objmsg.setIntProperty("Id", 1);
        objmsg.setStringProperty("City", "Philadelphia");
        objmsg.setText(1 + ":" + "message# " + 1 + ":" + 500);
        objmsg.setIntProperty("Priority", 2);
        objmsg.setJMSCorrelationID("" + 12);
        objmsg.setJMSPriority(2);
        publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 2, AQjmsConstants.EXPIRATION_NEVER);
        publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 3, AQjmsConstants.EXPIRATION_NEVER);
        System.out.println("Commit now and sleep...");
        topicSession.commit();
    }

    private static void receiveMessages(QueueSession qsess, AQjmsConsumer[] subs) throws JMSException {
        System.out.println("Receive test messages...");
        for (int i = 0; i < subs.length; i++) {
            System.out.println("\n\nMessages for subscriber : " + i);
            if (subs[i].getMessageSelector() != null) {
                System.out.println("  with selector: " + subs[i].getMessageSelector());
            }
            boolean done = false;
            while (!done) {
                try {
                    TextMessage robjmsg = (TextMessage) (subs[i].receiveNoWait());
                    if (robjmsg != null) {
                        String rTextMsg = robjmsg.getText();
                        System.out.println("rTextMsg " + rTextMsg);
                        System.out.print(" Pri: " + robjmsg.getJMSPriority());
                        System.out.print(" Message: " + robjmsg.getIntProperty("Id"));
                        System.out.print(" " + robjmsg.getStringProperty("City"));
                        System.out.println(" " + robjmsg.getIntProperty("Priority"));
                    } else {
                        System.out.println("No more messages.");
                        done = true;
                    }
                    qsess.commit();
                } catch (Exception e) {
                    System.out.println("Error in performJmsOperations: " + e);
                    done = true;
                }
            }
        }
    }

    String unschedulePropagation(DataSource dataSource, String topicUser,
                                 String topicPassword, String topicName, String linkName) {
        String resultString = "unschedulePropagation dataSource = [" + dataSource + "], " +
                "topicUser = [" + topicUser + "], topicPassword = [" + topicPassword + "], " +
                "topicName = [" + topicName + "], linkName = [" + linkName + "]";
        System.out.println(resultString);
        TopicConnection tconn;
        try {
            tconn = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection(
                    topicUser, topicPassword);
            TopicSession tsess = tconn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            Topic topic1 = ((AQjmsSession) tsess).getTopic(topicUser, topicName);
            ((AQjmsDestination) topic1).unschedulePropagation(tsess, linkName);
            resultString += "success";
        } catch (JMSException e) {
            e.printStackTrace();
            resultString += e.toString();
        }
        return resultString;
    }

    String deleteUsers(DataSource orderpdbDataSource, DataSource inventorypdbDataSource) throws SQLException {
        String returnValue = "";
        try {
            Connection connection = orderpdbDataSource.getConnection();
            System.out.println("orderpdbDataSource connection:" + connection);
            connection.createStatement().execute("drop user ORDERUSER cascade ");
            returnValue += " ORDERUSER dropped successfully";
        } catch (SQLException ex) {
            ex.printStackTrace();
            returnValue += ex.toString();
        }
        try {
            Connection connection = inventorypdbDataSource.getConnection();
            System.out.println("inventorypdbDataSource connection:" + connection);
            connection.createStatement().execute("drop user INVENTORYUSER cascade ");
            returnValue += " INVENTORYUSER dropped successfully";
        } catch (SQLException ex) {
            ex.printStackTrace();
            returnValue += ex.toString();
        }
        return returnValue;
    }
}

