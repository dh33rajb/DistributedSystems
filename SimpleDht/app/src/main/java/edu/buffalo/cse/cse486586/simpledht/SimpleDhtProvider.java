package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.List;
import java.util.Map;
import java.util.Scanner;

// We need to implement insert, query and delete functions
public class SimpleDhtProvider extends ContentProvider {

    private DatabaseAdapter dbAdapter;
    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    private static Uri uri;
    private String predecessorHash;
    private String successorHash;
    private static List<Integer> nodesInRing;
    private static List<String> hashNodesInRing;
    private static String allMessageStringChord;
    private static boolean allMessagesReceivedFlag;
    private static int msgReceivedCount;

    private static int MY_PORT;
    private static final int SERVER_PORT = 10000;
    private static final String DELIMITER = "/";
    private static final String QUERY_RESPONSE_DELIMITER = ";";


    Map<String, Integer> nodeHashMap;

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
            int flag = 0;

            boolean caseZero = predecessorHash.equals("") && successorHash.equals("");
            boolean caseOne = keyHash.compareTo(predecessorHash) > 0 && keyHash.compareTo(currentPortHash) <= 0;
            boolean caseTwo = currentPortHash.compareTo(predecessorHash) <= 0 && (keyHash.compareTo(predecessorHash) > 0) && (keyHash.compareTo(currentPortHash) >= 0);
            boolean caseThree = currentPortHash.compareTo(predecessorHash) <= 0 && (keyHash.compareTo(predecessorHash) < 0) && (keyHash.compareTo(currentPortHash) <= 0);

            if (caseZero || caseOne || caseTwo || caseThree) // Case-1: If pred and succ are not there
                dbAdapter.delete(selection, selectionArgs);
            else
                new DeleteForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(successorHash)), selection); // send request to successor node
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
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

            boolean caseZero = predecessorHash.equals("") && successorHash.equals("");
            boolean caseOne = keyHash.compareTo(predecessorHash) > 0 && keyHash.compareTo(currentPortHash) <= 0;
            boolean caseTwo = currentPortHash.compareTo(predecessorHash) <= 0 && (keyHash.compareTo(predecessorHash) > 0) && (keyHash.compareTo(currentPortHash) >= 0);
            boolean caseThree = currentPortHash.compareTo(predecessorHash) <= 0 && (keyHash.compareTo(predecessorHash) < 0) && (keyHash.compareTo(currentPortHash) <= 0);
            // Step-1: Check if the insert has to be processed by this node, if yes.. update to DB; else, send it to the right node who needs to update to its DB.
            if (caseZero || caseOne || caseTwo || caseThree) { // Case-1: If pred and succ are not there
                Cursor cur = dbAdapter.query(null, (String) values.get("key"), null, null);
                if (cur.getCount() == 0) {
                    dbAdapter.insert(values);
                    Log.v("insert", values.toString());
                } else {
                    dbAdapter.update(values);
                    Log.v("update", values.toString());
                }
            } else
                new InsertForwardClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(MY_PORT), String.valueOf(nodeHashMap.get(successorHash)), key, value);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        // Step-0: Initialization
        dbAdapter = new DatabaseAdapter(getContext());
        predecessorHash = "";
        successorHash = "";
        nodesInRing = new ArrayList<Integer>();

        allMessageStringChord = "";
        allMessagesReceivedFlag = false;
        msgReceivedCount = 0;

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledht.provider");
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

        // Step-3: Send node join message to all other devices. The message that we need to send is the port number of the device that joined the ring
        if ((Integer.valueOf(myPort) != 11108))
            new NodeJoinClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, myPort);
        nodesInRing.add(11108);

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        Cursor cursor = null;
        // We handle different scenarios here

        if (selection.compareTo("\"@\"") == 0) {
            cursor = dbAdapter.query(projection, selection, selectionArgs, sortOrder);
        } else if (selection.compareTo("\"*\"") == 0) {
            if (!successorHash.equals("")) {
                // forward request to all server tasks to print their values
                MatrixCursor matCursor = new MatrixCursor(new String[]{
                        "key", "value"
                });

                new starQueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), selection);
                while (!allMessagesReceivedFlag) {
                    // wait till all messages are received
                }
                allMessagesReceivedFlag = false;
                msgReceivedCount = 0;
                String[] otherDeviceMessages = allMessageStringChord.split(QUERY_RESPONSE_DELIMITER);
                allMessageStringChord = "";
                for (String str : otherDeviceMessages) {
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        matCursor.addRow(cursorEntry);
                    }
                }
                cursor = matCursor;

            } else {
                cursor = dbAdapter.query(projection, selection, selectionArgs, sortOrder);
            }
        } else {
            if (!successorHash.equals("")) {
                // forward request to all server tasks to print their values
                MatrixCursor matCursor = new MatrixCursor(new String[]{
                        "key", "value"
                });

                new starQueryRequestClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(MY_PORT), selection);
                while (!allMessagesReceivedFlag) {
                    // wait till all messages are received
                }
                allMessagesReceivedFlag = false;
                msgReceivedCount = 0;
                String[] otherDeviceMessages = allMessageStringChord.split(QUERY_RESPONSE_DELIMITER);
                allMessageStringChord = "";
                for (String str : otherDeviceMessages) {
                    if (!str.equals("")) {
                        String[] cursorEntry = str.split(":");
                        matCursor.addRow(cursorEntry);
                    }
                }
                cursor = matCursor;

            } else {
                cursor = dbAdapter.query(projection, selection, selectionArgs, sortOrder);
            }
        }

        Log.v("query", selection);
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
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

                // Case-1: Node Join --> As a new node joins in we update the predecessor and successor
                if (line.contains("NodeJoin")) {
                    int portNumToAdd = Integer.valueOf(line.split(DELIMITER)[0]);
                    nodesInRing.add(portNumToAdd);
                    updatePredAndSuccNodes();

                    // now send the updated node list to all other nodes
                    String nodesListString = "";
                    for (int n : nodesInRing) {
                        nodesListString = nodesListString + n + DELIMITER;
                    }
                    for (int i = 1; i < nodesInRing.size(); i++) {
                        new ChordJoinResponse().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, nodesListString, String.valueOf(nodesInRing.get(i)));
                    }
                } else if (line.contains("NodeResponse")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String[] lineSplitNew = Arrays.copyOf(lineSplit, lineSplit.length - 2);
                    updatePredAndSuccNodes(lineSplitNew);
                }

                // Case-2: Check if we have the right node and insert. if we have the worng node, forward again
                else if (line.contains("CheckNodeAndInsertKey")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String receiverPort = lineSplit[1];
                    String key = lineSplit[2];
                    String value = lineSplit[3];

                    ContentValues cValues = new ContentValues();
                    cValues.put("key", key);
                    cValues.put("value", value);
                    insert(uri, cValues);
                }

                // case-3: Fetch the Query response for the current remote port and forward it to the requestor (sender) port
                else if (line.contains("StarQueryRequest")) {
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String selection = lineSplit[1];
                    Cursor cursor = dbAdapter.query(null, selection, null, null);
                    // now send cursor values aas strings
                    String cursorResponseMesasges = ""; // key1:value1/ key2:value2/ etc..
                    if (cursor != null && cursor.getCount() != 0) {
                        cursor.moveToFirst();
                        do {
                            String key = cursor.getString(cursor.getColumnIndex("key"));
                            String valuee = cursor.getString(cursor.getColumnIndex("value"));
                            cursorResponseMesasges = cursorResponseMesasges + key + ":" + valuee + QUERY_RESPONSE_DELIMITER;
                        } while (cursor.moveToNext());
                    }
                    new starQueryResponseClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, senderPort, String.valueOf(MY_PORT), cursorResponseMesasges);
                }

                // case-4 Start query response
                else if (line.contains("StartQueryResponse")) {
                    msgReceivedCount++;
                    String lineSplit[] = line.split(DELIMITER);
                    String senderPort = lineSplit[0];
                    String receiverPort = lineSplit[1];
                    String queryResponse = lineSplit[2];
                    allMessageStringChord = allMessageStringChord + queryResponse + QUERY_RESPONSE_DELIMITER;
                    if (msgReceivedCount == nodesInRing.size()) {
                        allMessagesReceivedFlag = true;
                    }

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

    /* Send a node join message to all the other devices.
     * If a device is not alive, then anyway message will not be sent.
     */
    private class NodeJoinClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            // String[] REMOTE_PORTS = new String[]{"11108", "11112", "11116", "11120", "11124"};

            String msgToSend = msgs[0] + DELIMITER + "NodeJoin"; // sending the port number of the device that just joined the ring
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        11108);
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "NodeJoinClientTask: ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "NodeJoinClientTask: ClientTask socket IOException");
            }
            return null;
        }
    }

    private class ChordJoinResponse extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            // String[] REMOTE_PORTS = new String[]{"11112", "11116", "11120", "11124"};
            String msgToSend = msgs[0] + DELIMITER + "NodeResponse"; // sending the port number of the device that just joined the ring
            int remotePort = Integer.valueOf(msgs[1]);
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                // writing to view
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(msgToSend);
                printWriter.flush();
                printWriter.close();
                socket.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "ChordJoinResponse: ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ChordJoinResponse: ClientTask socket IOException");
            }
            return null;
        }
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

            myPortHash = genHash(Integer.toString(MY_PORT / 2));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // Step-2: find the predecessor and successor of the current node... Need to handle the first node and last node's pred and succ too.. kinda circular linked list

        if (hashNodesInRing.size() > 1) {
            for (int i = 0; i < hashNodesInRing.size(); i++) {
                if (hashNodesInRing.get(i).equals(myPortHash) && i == 0) {
                    predecessorHash = hashNodesInRing.get(hashNodesInRing.size() - 1);
                    successorHash = hashNodesInRing.get(i + 1);
                } else if (hashNodesInRing.get(i).equals(myPortHash) && i == (hashNodesInRing.size() - 1)) {
                    predecessorHash = hashNodesInRing.get(i - 1);
                    successorHash = hashNodesInRing.get(0);
                } else if (hashNodesInRing.get(i).equals(myPortHash)) {
                    predecessorHash = hashNodesInRing.get(i - 1);
                    successorHash = hashNodesInRing.get(i + 1);
                }
            }
        }
    }

    private void updatePredAndSuccNodes(String[] ports) {
        nodeHashMap = new HashMap<String, Integer>();
        hashNodesInRing = new ArrayList<String>();
        String myPortHash = "";

        nodesInRing = new ArrayList<Integer>();
        for (String s : ports) {
            nodesInRing.add(Integer.valueOf(s));
        }
        // Step-1: find hash values of all alive nodes and add them to a list in a sorted order
        try {
            for (int n : nodesInRing) {
                String hashOfNode = genHash(Integer.toString(n / 2));
                hashNodesInRing.add(hashOfNode);
                nodeHashMap.put(hashOfNode, n);
            }
            Collections.sort(hashNodesInRing);

            myPortHash = genHash(Integer.toString(MY_PORT / 2));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // Step-2: find the predecessor and successor of the current node... Need to handle the first node and last node's pred and succ too.. kinda circular linked list

        if (hashNodesInRing.size() > 1) {
            for (int i = 0; i < hashNodesInRing.size(); i++) {
                if (hashNodesInRing.get(i).equals(myPortHash) && i == 0) {
                    predecessorHash = hashNodesInRing.get(hashNodesInRing.size() - 1);
                    successorHash = hashNodesInRing.get(i + 1);
                } else if (hashNodesInRing.get(i).equals(myPortHash) && i == (hashNodesInRing.size() - 1)) {
                    predecessorHash = hashNodesInRing.get(i - 1);
                    successorHash = hashNodesInRing.get(0);
                } else if (hashNodesInRing.get(i).equals(myPortHash)) {
                    predecessorHash = hashNodesInRing.get(i - 1);
                    successorHash = hashNodesInRing.get(i + 1);
                }
            }
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

    private class starQueryRequestClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String selection = msgs[1];
            String msgToSend = senderPort + DELIMITER + selection + DELIMITER + "StarQueryRequest"; // sending the port number of the device that just joined the ring
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
                Log.e(TAG, "starQueryRequestClientTask:ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "starQueryRequestClientTask:ClientTask socket IOException");
            }
            return null;
        }
    }

    private class starQueryResponseClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            String receiverPort = msgs[1];
            String queryResponse = msgs[2];
            String msgToSend = senderPort + DELIMITER + receiverPort + DELIMITER + queryResponse + DELIMITER + "StartQueryResponse"; // sending the port number of the device that just joined the ring
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