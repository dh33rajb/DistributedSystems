package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class SimpleDynamoProvider extends ContentProvider {

    static final String TAG = SimpleDynamoProvider.class.getSimpleName();
    private static final int SERVER_PORT = 10000;
    private static final String DELIMITER = "/";
    private static final String QUERY_RESPONSE_DELIMITER = ";";
    private static Uri uri;
    private static List<Integer> nodesInRing;
    private static List<String> hashNodesInRing;
    private static String allMessageStringChord;
    private static boolean allMessagesReceivedFlag;
    private static int msgReceivedCount;

    private static boolean threeMessagesReceivedFlag;
    private static int threeMsgReceivedCount;
    private static boolean timeoutFlag;
    private static long timeoutStartTime;

    private static int MY_PORT;
    Map<String, Integer> nodeHashMap;
    private DatabaseAdapter dbAdapter;

    private final int semaInit = 1;// here we are hardcoding that at any point in time only one thread can get into the critical section.
    private final int semaInitQuery = 1;
    DynamoSemaphore ds;
    DynamoSemaphore dqs;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if (selection.equals("\"@\"")) {
            boolean deleteSuccess = dbAdapter.delete(selection, selectionArgs);
        } else if (selection.equals("\"*\"")) {
            new StarDeleteClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), selection);
        } else {
            String keyHash = null;
            String currentPortHash = null;
            try {
                keyHash = genHash(selection);
                currentPortHash = genHash(String.valueOf(MY_PORT / 2));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            String rightHashValue = "";
            boolean caseZero = false;
            if (hashNodesInRing != null) {
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    if (i == 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else if (i == (hashNodesInRing.size() - 1) && keyHash.compareTo(hashNodesInRing.get(i)) > 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else {
                        if (i != 0 && keyHash.compareTo(hashNodesInRing.get(i - 1)) > 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                            rightHashValue = hashNodesInRing.get(i);
                    }
                }
            } else {
                caseZero = true;
            }
            if (caseZero) {
                System.out.println("&& Key: " + selection + " is served by node: " + MY_PORT + " when hashNodesInRing= " + hashNodesInRing.size());
                new DeleteForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(MY_PORT), selection); // send request to successor node
                return 0;
            } else {
                System.out.println("&& Key: " + selection + " is served by node: " + String.valueOf(nodeHashMap.get(rightHashValue)) + " when hashNodesInRing= " + hashNodesInRing.size());
                // The below code has been written under the assumption that there are always 5 nodes in the system, something mentioned in the proj doc.
                List<String> tempHashNodesInRing = new ArrayList<String>();
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    tempHashNodesInRing.add(hashNodesInRing.get(i));
                    if (i == (hashNodesInRing.size() - 1)) {
                        tempHashNodesInRing.add(hashNodesInRing.get(0));
                        tempHashNodesInRing.add(hashNodesInRing.get(1));
                    }
                }
                for (int i = 0; i < tempHashNodesInRing.size() - 2; i++) {
                    if (tempHashNodesInRing.get(i).equals(rightHashValue)) {
                        System.out.println("^^^ Key: " + selection + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))));
                        System.out.println("^^^ Key: " + selection + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))));
                        System.out.println("^^^ Key: " + selection + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 2))));

                        new DeleteForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))), selection); // send request to successor node*/
                        new DeleteForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))), selection); // send request to successor node*/
                        new DeleteForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 2))), selection); // send request to successor node*/
                        break;
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        // TODO Auto-generated method stub
        try {
            String key = values.getAsString("key");
            String value = values.getAsString("value");
            String currentPortHash = null;
            String keyHash = genHash(key);
            currentPortHash = genHash(String.valueOf(MY_PORT / 2));
            int flag = 0;

            /* Check if the current node need to serve the request:
                1. If yes, serve it.
                2. else, forward to right node.

                Step-1: Create a temporary list of hashNodesInRing --> form a circular kind of list --> last elem - 1st elem - 2nd elem - .... - last elem - 1st elem
                Step-2: Find between which two hash keys the keyHash falls under

             */
            String rightHashValue = "";
            boolean caseZero = false;
            if (hashNodesInRing != null) {
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    if (i == 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else if (i == (hashNodesInRing.size() - 1) && keyHash.compareTo(hashNodesInRing.get(i)) > 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else {
                        if (i != 0 && keyHash.compareTo(hashNodesInRing.get(i - 1)) > 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                            rightHashValue = hashNodesInRing.get(i);
                    }
                }
            } else {
                caseZero = true;
            }
            // here we need to insert the data into the current node and also forward the data to the next two nodes
            if (caseZero) {
                System.out.println("@@ Key: " + key + " is served by node: " + MY_PORT);
                new InsertForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(MY_PORT), key, value);
                return null;
            } else {
                System.out.println("@@@ Key: " + key + " is served by node: " + String.valueOf(nodeHashMap.get(rightHashValue)) + " when hashNodesInRing= " + hashNodesInRing.size());
                // The below code has been written under the assumption that there are always 5 nodes in the system, something mentioned in the proj doc.
                List<String> tempHashNodesInRing = new ArrayList<String>();
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    tempHashNodesInRing.add(hashNodesInRing.get(i));
                    if (i == (hashNodesInRing.size() - 1)) {
                        tempHashNodesInRing.add(hashNodesInRing.get(0));
                        tempHashNodesInRing.add(hashNodesInRing.get(1));
                    }
                }
                for (int i = 0; i < tempHashNodesInRing.size() - 2; i++) {
                    if (tempHashNodesInRing.get(i).equals(rightHashValue)) {
                        System.out.println("### Key: " + key + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))));
                        System.out.println("### Key: " + key + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))));
                        System.out.println("### Key: " + key + " is served by node: " + String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 2))));
                        new InsertForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))), key, value);
                        new InsertForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))), key, value);
                        new InsertForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 2))), key, value);
                        break;
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        // Step-0: Initialization
        timeoutFlag = false;

        ds = new DynamoSemaphore(semaInit);
        dqs = new DynamoSemaphore(semaInitQuery);
        dbAdapter = new DatabaseAdapter(getContext());
        nodesInRing = new ArrayList<Integer>(Arrays.asList(11108, 11112, 11116, 11120, 11124));
        updatePredAndSuccNodes();
        allMessageStringChord = "";
        allMessagesReceivedFlag = false;
        threeMessagesReceivedFlag = false;
        threeMsgReceivedCount = 0;
        msgReceivedCount = 0;

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamoProvider");
        uriBuilder.scheme("content");
        uri = uriBuilder.build();

        // Step-1: Get port number of the current device
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        MY_PORT = Integer.valueOf(myPort);

        // Step-2: Start server socket here
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }
        return false;
    }

    private void updatePredAndSuccNodes() {
        nodeHashMap = new HashMap<String, Integer>();
        hashNodesInRing = new ArrayList<String>();
        String myPortHash = "";
        // Step-1: find hash values of all alive nodes and add them to a list in a sorted order
        try {
            for (int n : nodesInRing) {
                String hashOfNode = genHash(Integer.toString(n / 2));
                hashNodesInRing.add(hashOfNode);
                nodeHashMap.put(hashOfNode, n);
            }
            Collections.sort(hashNodesInRing);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        System.out.println("QUERY LOCK ACQUIRED");
        dqs.acquire();
        // wait for 1 sec
        try {
            System.out.println("1000 START");
            Thread.sleep(1000);
            System.out.println("1000 END");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        dqs.release();
        System.out.println("QUERY LOCKE RELEASED");
        // TODO Auto-generated method stub
        System.out.println("##Queried the key: " + selection);
        Cursor cursor = null;
        // We handle different scenarios here

        if (selection.compareTo("\"@\"") == 0) {
            // Step-1: Find the current port hash
            String currentPortHash = null;
            try {
                currentPortHash = genHash(String.valueOf(MY_PORT / 2));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            // Step-2: find the next two hashValues in the ring and send request to them to return their @ values
            // The below code has been written under the assumption that there are always 5 nodes in the system, something mentioned in the proj doc.
            List<String> tempHashNodesInRing = new ArrayList<String>();
            for (int i = 0; i < hashNodesInRing.size(); i++) {
                tempHashNodesInRing.add(hashNodesInRing.get(i));
                if (i == (hashNodesInRing.size() - 1)) {
                    tempHashNodesInRing.add(hashNodesInRing.get(0));
                    tempHashNodesInRing.add(hashNodesInRing.get(1));
                }
            }

            for (int i = 1; i < tempHashNodesInRing.size() - 1; i++) {
                if (tempHashNodesInRing.get(i).equals(currentPortHash)) {
                    System.out.println("%%%The right port for the query: " + selection + " is: " + nodeHashMap.get(tempHashNodesInRing.get(i)));
                    new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i - 1))), selection, "GeneralQuery");
                    new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))), selection, "GeneralQuery");
                    new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))), selection, "GeneralQuery");
                    break;
                }
            }
            // SEMAPHORE WAIT HERE
            ds.acquire();
            while (!threeMessagesReceivedFlag) {
                // wait till the message is received
            }
            threeMessagesReceivedFlag = false;
            threeMsgReceivedCount = 0;
            String[] otherDeviceMessages = allMessageStringChord.split(QUERY_RESPONSE_DELIMITER);
            System.out.println("%%% Out of @ message received loop");
            allMessageStringChord = "";

            // Step-3: here we need to keep only the latest version key-value pair in the cursor and remove rest of the others
            Map<String, String> cursorMap = new LinkedHashMap<String, String>();
            for (String str : otherDeviceMessages) {
                if (!str.equals("")) {
                    String[] cursorEntry = str.split(":");
                    if (cursorMap.get(cursorEntry[0]) != null) {
                        String mapVal = cursorMap.get(cursorEntry[0]);
                        String strr[] = mapVal.split(":");
                        if (Integer.valueOf(cursorEntry[2]) > Integer.valueOf(strr[2])) {
                            cursorMap.put(cursorEntry[0], str);
                        } else if (Integer.valueOf(cursorEntry[2]) == Integer.valueOf(strr[2])) {
                            if (Long.valueOf(cursorEntry[3]) > Long.valueOf(strr[3])) {
                                cursorMap.put(cursorEntry[0], str);
                            }
                        }
                    } else {
                        cursorMap.put(cursorEntry[0], str);
                    }
                }
            }
            // Step-4: We need to remove the keys that do not belong to the current partition

            /*
            The current node must give the following for @:

            1. If keyHash belongs two any of prev two nodes, they must be returned.
            2. If keyHash belongs to current node hash, it must be returned.
            3. If keyHash belongs to current node hash and are present in next two nodes, they must be returned.

            1. The current node keys... can be obtained from current node / next two nodes
            2. The previous node's keys


            */
            Map<String, String> cursorMapTwo = new LinkedHashMap<String, String>();
            for (Map.Entry e : cursorMap.entrySet()) {
                String key = (String) e.getKey();
                String keyHash = null;
                try {
                    keyHash = genHash(key);
                } catch (NoSuchAlgorithmException ex) {
                    ex.printStackTrace();
                }
                String rightHashValue = "";
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    if (i == 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else if (i == (hashNodesInRing.size() - 1) && keyHash.compareTo(hashNodesInRing.get(i)) > 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else {
                        if (i != 0 && keyHash.compareTo(hashNodesInRing.get(i - 1)) > 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                            rightHashValue = hashNodesInRing.get(i);
                    }
                }
                for (int j = 2; j < tempHashNodesInRing.size(); j++) {
                    if (tempHashNodesInRing.get(j).compareTo(currentPortHash) == 0) {
                        if (rightHashValue.compareTo(tempHashNodesInRing.get(j)) == 0 || rightHashValue.compareTo(tempHashNodesInRing.get(j - 1)) == 0 || rightHashValue.compareTo(tempHashNodesInRing.get(j - 2)) == 0) {
                            cursorMapTwo.put((String) e.getKey(), (String) e.getValue());
                        }
                    }
                }
            }

            MatrixCursor matCursor = new MatrixCursor(new String[]{
                    "key", "value"
            });
            for (Map.Entry e : cursorMapTwo.entrySet()) {
                System.out.println(">>>>>>>>>>>>>>> Key: " + e.getKey() + " Value: " + e.getValue());
                String str = (String) e.getValue();
                if (!str.equals("")) {
                    String[] cursorEntry = str.split(":");
                    matCursor.addRow(Arrays.copyOfRange(cursorEntry, 0, 2));
                    System.out.println(cursorEntry[0] + "-->" + cursorEntry[1] + "-->" + cursorEntry[2]);
                }
            }
            cursor = matCursor;

        } else if (selection.compareTo("\"*\"") == 0) {
            if (hashNodesInRing == null || hashNodesInRing.size() == 0) {
                cursor = dbAdapter.query(projection, selection, selectionArgs, sortOrder);
            } else {
                // forward request to all server tasks to print their values
                MatrixCursor matCursor = new MatrixCursor(new String[]{
                        "key", "value"
                });
                for (int n : nodesInRing) {
                    new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(n), selection, "StarQuery");
                }

                // Need to also set a timeout here.. if an avd is down, we do not need to wait for flag to change, but just proceed wit what we have
                if (!timeoutFlag) {
                    timeoutStartTime = System.currentTimeMillis();
                    timeoutFlag = true;
                }
                while (!allMessagesReceivedFlag && (System.currentTimeMillis() - timeoutStartTime) < 3000) {
                    // wait till the message is received
                }
                timeoutFlag = false;
                allMessagesReceivedFlag = false;
                msgReceivedCount = 0;
                String[] otherDeviceMessages = allMessageStringChord.split(QUERY_RESPONSE_DELIMITER);
                allMessageStringChord = "";

                // here we need to keep only the latest version key-value pair in the cursor and remove rest of the others
                Map<String, String> cursorMap = new LinkedHashMap<String, String>();
                for (String str : otherDeviceMessages) {
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        if (cursorMap.get(cursorEntry[0]) != null) {
                            String mapVal = cursorMap.get(cursorEntry[0]);
                            String strr[] = mapVal.split(":");
                            if (Integer.valueOf(cursorEntry[2]) > Integer.valueOf(strr[2])) {
                                cursorMap.put(cursorEntry[0], str);
                            } else if (Integer.valueOf(cursorEntry[2]) == Integer.valueOf(strr[2])) {
                                if (Long.valueOf(cursorEntry[3]) > Long.valueOf(strr[3])) {
                                    cursorMap.put(cursorEntry[0], str);
                                }
                            }
                        } else {
                            cursorMap.put(cursorEntry[0], str);
                        }
                    }
                }

                for (Map.Entry e : cursorMap.entrySet()) {
                    String str = (String) e.getValue();
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        matCursor.addRow(Arrays.copyOfRange(cursorEntry, 0, 2));
                        System.out.println(cursorEntry[0] + "-->" + cursorEntry[1] + "-->" + cursorEntry[2]);
                    }
                }
                cursor = matCursor;
            }
        } else {
            // forward request to all server tasks to print their values
            MatrixCursor matCursor = new MatrixCursor(new String[]{
                    "key", "value"
            });
            String keyHash = null;
            String currentPortHash = null;
            try {
                keyHash = genHash(selection);
                currentPortHash = genHash(String.valueOf(MY_PORT / 2));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            String rightHashValue = "";
            boolean caseZero = false;
            if (hashNodesInRing != null) {
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    if (i == 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else if (i == (hashNodesInRing.size() - 1) && keyHash.compareTo(hashNodesInRing.get(i)) > 0)
                        rightHashValue = hashNodesInRing.get(0);
                    else {
                        if (i != 0 && keyHash.compareTo(hashNodesInRing.get(i - 1)) > 0 && keyHash.compareTo(hashNodesInRing.get(i)) <= 0)
                            rightHashValue = hashNodesInRing.get(i);
                    }
                }
            } else {
                caseZero = true;
            }
            if (caseZero) {
                cursor = dbAdapter.query(projection, selection, selectionArgs, sortOrder);
            } else {

                // here we need to retrieve the most recent version of the key in the DB
                // The below code has been written under the assumption that there are always 5 nodes in the system, something mentioned in the proj doc.
                List<String> tempHashNodesInRing = new ArrayList<String>();
                for (int i = 0; i < hashNodesInRing.size(); i++) {
                    tempHashNodesInRing.add(hashNodesInRing.get(i));
                    if (i == (hashNodesInRing.size() - 1)) {
                        tempHashNodesInRing.add(hashNodesInRing.get(0));
                        tempHashNodesInRing.add(hashNodesInRing.get(1));
                    }
                }
                for (int i = 0; i < tempHashNodesInRing.size() - 2; i++) {
                    if (tempHashNodesInRing.get(i).equals(rightHashValue)) {
                        System.out.println("%%%The right port for the query: " + selection + " is: " + nodeHashMap.get(tempHashNodesInRing.get(i)));
                        new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i))), selection, "GeneralQuery");
                        new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 1))), selection, "GeneralQuery");
                        new QueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), String.valueOf(nodeHashMap.get(tempHashNodesInRing.get(i + 2))), selection, "GeneralQuery");
                        break;
                    }
                }
                // SEMAPHORE WAIT HERE
                ds.acquire();
                // Need to also set a timeout here.. if an avd is down, we do not need to wait for flag to change, but just proceed wit what we have
                if (!timeoutFlag) {
                    timeoutStartTime = System.currentTimeMillis();
                    timeoutFlag = true;
                }
                while (!threeMessagesReceivedFlag && (System.currentTimeMillis() - timeoutStartTime) < 3000) {
                    // wait till the message is received
                }
                timeoutFlag = false;
                threeMessagesReceivedFlag = false;
                threeMsgReceivedCount = 0;
                String[] otherDeviceMessages = allMessageStringChord.split(QUERY_RESPONSE_DELIMITER);
                System.out.println("+++ Out of single message received loop");
                allMessageStringChord = "";

                // here we need to keep only the latest version key-value pair in the cursor and remove rest of the others
                Map<String, String> cursorMap = new LinkedHashMap<String, String>();
                for (String str : otherDeviceMessages) {
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        if (cursorMap.get(cursorEntry[0]) != null) {
                            String mapVal = cursorMap.get(cursorEntry[0]);
                            String strr[] = mapVal.split(":");
                            if (Integer.valueOf(cursorEntry[2]) > Integer.valueOf(strr[2])) {
                                cursorMap.put(cursorEntry[0], str);
                            } else if (Integer.valueOf(cursorEntry[2]) == Integer.valueOf(strr[2])) {
                                if (Long.valueOf(cursorEntry[3]) > Long.valueOf(strr[3])) {
                                    cursorMap.put(cursorEntry[0], str);
                                }
                            }
                        } else {
                            cursorMap.put(cursorEntry[0], str);
                        }
                    }
                }
                for (Map.Entry e : cursorMap.entrySet()) {
                    String str = (String) e.getValue();
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        matCursor.addRow(Arrays.copyOfRange(cursorEntry, 0, 2));
                        System.out.println(cursorEntry[0] + "-->" + cursorEntry[1] + "-->" + cursorEntry[2]);
                    }
                }
                cursor = matCursor;
            }
        }
        Log.v("query", selection);
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            // Dheeraj: changes start
            Socket clientSocket = null;
            InputStream inputStream = null;
            boolean trueFlag = true;
            while (trueFlag) {
                Scanner in = null;
                String line = "";
                try {
                    clientSocket = serverSocket.accept();
                    inputStream = clientSocket.getInputStream();
                    in = new Scanner(inputStream);
                    while (in.hasNext()) {
                        System.out.println(line = in.nextLine());
                    }
                } catch (IOException ioException) {
                    System.err.println("ServerTask IOException while getting input stream");
                } finally {
                    try {
                        if (in != null) in.close();
                        if (inputStream != null) inputStream.close();
                        if (clientSocket != null) clientSocket.close();
                    } catch (IOException i) {
                        System.err.println("ServerTask IOException while closing streams");
                    }
                }
                final String passedMsg = line;

                // Case-2: Check if we have the right node and insert. if we have the worng node, forward again
                if (line.contains("CheckNodeAndInsertKey")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String receiverPort = lineSplit[1];
                    String key = lineSplit[2];
                    String value = lineSplit[3];

                    ContentValues values = new ContentValues();
                    values.put("key", key);
                    values.put("value", value);
                    Cursor cur = dbAdapter.query(null, (String) values.get("key"), null, null);
                    if (cur.getCount() == 0) {
                        dbAdapter.insert(values);
                        Log.v("insert", values.toString());
                    } else {
                        dbAdapter.update(values);
                        Log.v("update", values.toString());
                    }
                }

                // case-3: Fetch the Query response for the current remote port and forward it to the requestor (sender) port
                else if (line.contains("QueryRequest")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String selection = lineSplit[1];
                    String queryType = lineSplit[2];
                    Cursor cursor = null;
                    cursor = dbAdapter.query(null, selection, null, null);
                    // now send cursor values aas strings
                    String cursorResponseMesasges = ""; // key1:value1/ key2:value2/ etc..
                    if (cursor != null && cursor.getCount() != 0) {
                        cursor.moveToFirst();
                        do {
                            String key = cursor.getString(cursor.getColumnIndex("key"));
                            String value = cursor.getString(cursor.getColumnIndex("value"));
                            String version = cursor.getString(cursor.getColumnIndex("version"));
                            String unixEpoch = cursor.getString(cursor.getColumnIndex("unix_epoch")).trim();
                            cursorResponseMesasges = cursorResponseMesasges + key + ":" + value + ":" + version + ":" + unixEpoch + QUERY_RESPONSE_DELIMITER;
                        } while (cursor.moveToNext());
                    }
                    new QueryResponseClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, senderPort, String.valueOf(MY_PORT), cursorResponseMesasges, queryType);
                }

                // case-4 Start query response
                else if (line.contains("QueryResponse")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String receiverPort = lineSplit[1];
                    String queryResponse = lineSplit[2];
                    String queryType = lineSplit[3];
                    allMessageStringChord = allMessageStringChord + queryResponse + QUERY_RESPONSE_DELIMITER;
                    if (queryType.equals("StarQuery"))
                        msgReceivedCount++;
                    else if (queryType.equals("GeneralQuery"))
                        threeMsgReceivedCount++;
                    if (queryType.equals("StarQuery") && msgReceivedCount == nodesInRing.size()) {
                        allMessagesReceivedFlag = true;
                    } else if (queryType.equals("GeneralQuery") && (threeMsgReceivedCount == 3 || threeMsgReceivedCount == nodesInRing.size())) {
                        threeMessagesReceivedFlag = true;
                    }
                    // SEMAPHORE RELEASE HERE
                    ds.release();
                }

                // Case-7: StarDeleteRequest
                else if (line.contains("StarDeleteRequest")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String selection = "*";
                    boolean deleteSuccess = dbAdapter.delete(selection, null);
                }

                // case-8: DeleteKeyRequest
                else if (line.contains("DeleteKeyRequest")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String selection = lineSplit[2];
                    boolean deleteSuccess = dbAdapter.delete(selection, null);
                }
            }
            return null;
        }
    }

    private class InsertForwardClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String receiverPort = msgs[1];
            String key = msgs[2];
            String value = msgs[3];
            String msgToSend = msgs[0] + DELIMITER + receiverPort + DELIMITER + key + DELIMITER + value + DELIMITER + "CheckNodeAndInsertKey"; // sending the port number of the device that just joined the ring
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(receiverPort));
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "InsertForwardClientTask: ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "InsertForwardClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }

    private class QueryRequestClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String receiverPort = msgs[1];
            String selection = msgs[2];
            String queryType = msgs[3];
            String msgToSend = senderPort + DELIMITER + selection + DELIMITER + queryType + DELIMITER + "QueryRequest"; // sending the port number of the device that just joined the ring
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.valueOf(receiverPort));
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "QueryRequestClientTask:ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "QueryRequestClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }

    private class QueryResponseClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String receiverPort = msgs[1];
            String queryResponse = msgs[2];
            String queryType = msgs[3];
            String msgToSend = senderPort + DELIMITER + receiverPort + DELIMITER + queryResponse + DELIMITER + queryType + DELIMITER + "QueryResponse"; // sending the port number of the device that just joined the ring
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.valueOf(senderPort));
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "starQueryResponseClientTask:ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "starQueryResponseClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }

    private class StarDeleteClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String selection = msgs[1];
            String msgToSend = senderPort + DELIMITER + selection + DELIMITER + "StarDeleteRequest"; // sending the port number of the device that just joined the ring
            try {
                for (int n : nodesInRing) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), n);
                    // writing to view
                    OutputStream outputStream = socket.getOutputStream();
                    PrintWriter printWriter = new PrintWriter(outputStream);
                    printWriter.write(msgToSend);
                    printWriter.flush();
                    printWriter.close();
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "StarDeleteClientTask:ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "StarDeleteClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }


    private class DeleteForwardClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String receiverPort = msgs[1];
            String key = msgs[2];
            String msgToSend = msgs[0] + DELIMITER + receiverPort + DELIMITER + key + DELIMITER + "DeleteKeyRequest"; // sending the port number of the device that just joined the ring
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(receiverPort));
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "DeleteForwardClientTask:ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "DeleteForwardClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }
}