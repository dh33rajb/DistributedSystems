package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static String[] REMOTE_PORTS = new String[]{"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;

    public static final String MESSAGE_COUNTER_PREF = "MessageCounterPrefs";
    public SharedPreferences msgPrefs;

    // Group Messenger-2 Entries
    private final String DELIMITER = "/";

    private int messageCounter;
    private int si;

    private static Queue<List<String>> holdBackQueue;
    private String UNDELIVERABLE = "undeliverable";
    private String DELIVERABLE = "deliverable";

    private int aliveAvdCount;

    private static Map<String, List<String[]>> proposedSequenceListMap;
    private static String local_port;

    private static long appStartTime;

    String failed_port;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // initialization
        aliveAvdCount = 5;
        messageCounter = 0;
        si = 0;
        holdBackQueue = new LinkedList<List<String>>();
        proposedSequenceListMap = new HashMap<String, List<String[]>>();
        appStartTime = System.currentTimeMillis();

        super.onCreate(savedInstanceState);

        getSharedPreferences(MESSAGE_COUNTER_PREF, 0).edit().clear().commit();
        msgPrefs = getSharedPreferences(MESSAGE_COUNTER_PREF, 0);

        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        local_port = myPort;

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            ServerSocket tempSocket = new ServerSocket(Integer.valueOf(myPort));
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket, tempSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = editText.getText().toString() + "\n";
                TextView localTextView = (TextView) findViewById(R.id.editText1);
                localTextView.append("\t" + msg); // This is one way to display a string.
                TextView remoteTextView = (TextView) findViewById(R.id.editText1);
                remoteTextView.append("\n");
                    /*
                     * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                     * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                     * the difference, please take a look at
                     * http://developer.android.com/reference/android/os/AsyncTask.html
                     */
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                msg = "";
                editText.setText(msg); // This is one way to reset the input box.
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    /**
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     * <p/>
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko & dheeraj
     * @reference Univ. of Maryland Android Threads, AsyncTasks & Handlers Tutorial: https://www.youtube.com/watch?v=Rh9cgaRw8n4
     * Android AsyncTask Tutorial: https://www.youtube.com/watch?v=V4q0sTIntsk
     * Android Socket Programming Tutorial: https://www.youtube.com/watch?v=ckWG3JXCCzM
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            ServerSocket tempSocket  = sockets[1];
            String locPort = String.valueOf(tempSocket.getLocalPort());
            System.out.println ("????????????????" + locPort + "??????????????????");
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
                        serverSocket.setSoTimeout(7000);
                        System.out.println(line = in.nextLine());
                    }
                } catch (SocketTimeoutException ste) {
                    // This is where we handle the INACTIVE / KILLED CLIENT APP part
                    // aliveAvdCount--;
                    // we'll now broadcast the message that an AVD went out to all other AVDs
                    System.out.println("SOCKET TIMEOUT OCCURRED");
                    Log.e(TAG, "Socket timeout.");
                    System.out.println("***2. The guy that failed is: " + locPort);
                    // new AvdDownClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, local_port);
                    String[] REM_PORTS = new String[]{"11108", "11112", "11116", "11120", "11124"};
                    for (String remotePort : REM_PORTS) {
                        try {
                            if (!remotePort.equals (locPort)) {
                                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remotePort));
                                String streamText = locPort + DELIMITER + "case4";
                                OutputStream outputStream = socket.getOutputStream();
                                PrintWriter printWriter = new PrintWriter(outputStream);
                                printWriter.write(streamText);
                                printWriter.flush();
                                printWriter.close();
                                outputStream.close();
                                socket.close();
                                System.out.println("*****Step-5*****" + streamText);
                            }
                        } catch (SocketTimeoutException se) {
                            Log.e(TAG, "Socket timeout.");
                        } catch (UnknownHostException e) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e) {
                            Log.e(TAG, "ClientTask socket IOException");
                        }
                    }
                    // return null;
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

                if (line.contains("case4")) {
                    String[] data = line.split("/");
                    String failedPort = data[0];
                    System.out.println("***1.The guy that failed is: " + failedPort);
                    aliveAvdCount--;
                    failed_port = failedPort;

                    int highest_si = 0;
                    String highest_sender = "0";
                    if (proposedSequenceListMap.size() > 0) {
                        Iterator<Map.Entry<String, List<String[]>>> itr = proposedSequenceListMap.entrySet().iterator();
                        while (itr.hasNext()) {
                            Map.Entry<String, List<String[]>> e = itr.next();
                            for (String[] s : e.getValue())
                                System.out.println("@@@@@@@" + s[0] + "-->" + s[1] + "-->" + s[2] + "-->" + s[3] + "@@@@@@@");
                            if (e.getValue().size() == aliveAvdCount) {
                                System.out.println("*****Step-1*****");
                                for (String[] s : e.getValue()) {
                                    if (Integer.valueOf(s[2]) == highest_si) {
                                        if (Integer.valueOf(highest_sender) > Integer.valueOf(s[1])) {
                                            highest_si = Integer.valueOf(s[2]);
                                            highest_sender = s[1];
                                        }
                                    } else if (Integer.valueOf(s[2]) > highest_si) {
                                        highest_si = Integer.valueOf(s[2]);
                                        highest_sender = s[1];
                                    }
                                }
                                System.out.println("*****Step-2*****");
                                itr.remove();
                                // TRANSMIT:   1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.Highest_SEQUENCE_NUMBER   4. REMOTE_PORT;
                                new MulticastClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, e.getKey(), local_port, String.valueOf(highest_si));
                            }
                        }
                    }
                }

                // ISIS Phase-2 (PART-1)
                // HOLD-BACK QUEUE: 1. MESSAGE_COUNTER; 2. MESSAGE;  3. LOCAL PORT;  4. REMOTE PORT / SUGGESTED PORT   5. SI
                else if (line.contains("case1")) {
                    // RECEIVE:   1. MESSAGE_COUNTER; 2. MESSAGE;  3. LOCAL_PORT;  4. REMOTE_PORT; 5. CASE_1
                    si = si + 1;
                    String[] data = line.split("/");
                    String msgCounter = data[0];
                    String msg = data[1];
                    String senderPort = data[2];
                    String receiverPrt = data[3];

                    // Transmit: 1.MESSAGE_COUNTER; 2. SI;  3.SENDER_PORT   4. RECEIVER_PORT
                    new ISISClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgCounter, String.valueOf(si), senderPort, receiverPrt);

                    // HOLD-BACK QUEUE
                    ArrayList<String> list = new ArrayList<String>();
                    list.add(msgCounter);
                    list.add(msg);
                    list.add(senderPort);
                    list.add(receiverPrt);
                    list.add(String.valueOf(si));
                    list.add(UNDELIVERABLE);
                    holdBackQueue.add(list);
                } else if (line.contains("case2")) {
                    // RECEIVE:   1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.SEQUENCE_NUMBER   4. REMOTE_PORT;
                    String[] data = line.split("/");
                    String msgCounter = data[0];
                    String senderPort = data[1];
                    String si = data[2];
                    String receiverPort = data[3];
                    if (proposedSequenceListMap.get(msgCounter) != null) {
                        List<String[]> propSeqList = proposedSequenceListMap.get(msgCounter);
                        propSeqList.add(data);
                        proposedSequenceListMap.put(msgCounter, propSeqList);
                    } else {
                        List<String[]> propSeqList = new ArrayList<String[]>();
                        propSeqList.add(data);
                        proposedSequenceListMap.put(msgCounter, propSeqList);
                    }
                    // now check if all proposed sequence for the messages has arrived, if it has, we need to send the final one to the server
                    int highest_si = 0;
                    String highest_sender = "0";
                    // *****************************************************
                    // NEED TO CHANGE THIS TO 5 WHILE ACTUAL IMPLEMENTATION
                    // *****************************************************
                    if (proposedSequenceListMap.get(msgCounter).size() == aliveAvdCount) {
                        for (String[] s : proposedSequenceListMap.get(msgCounter)) {
                            if (Integer.valueOf(s[2]) == highest_si) {
                                if (Integer.valueOf(highest_sender) > Integer.valueOf(s[1])) {
                                    highest_si = Integer.valueOf(s[2]);
                                    highest_sender = s[1];
                                }
                            } else if (Integer.valueOf(s[2]) > highest_si) {
                                highest_si = Integer.valueOf(s[2]);
                                highest_sender = s[1];
                            }
                        }
                        proposedSequenceListMap.remove(msgCounter);
                        // TRANSMIT:   1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.Highest_SEQUENCE_NUMBER   4. REMOTE_PORT;
                        new MulticastClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgCounter, senderPort, String.valueOf(highest_si), receiverPort);
                    }
                } else if (line.contains("case3")) {
                    System.out.println("*****Step-4*****");
                    // RECEIVE: 1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.Highest_SEQUENCE_NUMBER
                    String[] data = line.split("/");
                    System.out.println("####### --> " + data[0] + "--" + data[1] + "--" + data[2]);
                    String msgCounter = data[0];
                    String senderPort = data[1];
                    int sk = Integer.valueOf(data[2]);
                    int final_si = sk;
                    System.out.println(senderPort + "!!!!!!!!!>>>");
                    // HOLD-BACK QUEUE: 1. MESSAGE_COUNTER; 2. MESSAGE;  3. LOCAL PORT;  4. REMOTE PORT / SUGGESTED PORT   5. SI
                    for (List<String> al : holdBackQueue) {
                        if (al.get(0).equals(msgCounter) && al.get(2).equals(senderPort)) {
                            al.set(4, String.valueOf(final_si));
                            //al.set(3, String.valueOf(remotePort));
                            al.set(5, DELIVERABLE);
                        }
                    }
                    // HOLD-BACK QUEUE: 1. MESSAGE_COUNTER; 2. MESSAGE;  3. LOCAL PORT;  4. REMOTE PORT / SUGGESTED PORT
                    // 5. SI   6. UNDELIVERABLE

                    for (List<String> list : holdBackQueue) {
                        System.out.println(">>>>>>>>>>>>>" + list);
                    }

                    Collections.sort((List<List<String>>) holdBackQueue, new Comparator<List<String>>() {
                        @Override
                        public int compare(List<String> lhs, List<String> rhs) {
                            if (Integer.valueOf(lhs.get(4)).equals(Integer.valueOf(rhs.get(4))))
                                return Integer.valueOf(lhs.get(2)).compareTo(Integer.valueOf(rhs.get(2)));
                            else
                                return Integer.valueOf(lhs.get(4)).compareTo(Integer.valueOf(rhs.get(4)));

                        }
                    });

                    for (List<String> list : holdBackQueue) {
                        System.out.println("<<<<<<<<<<<<<<<<<" + list);
                    }
                    System.out.println("0000000000000>>>>" + failed_port + "<<<<00000000000");
                    int c = 0;
                    for (List<String> al : holdBackQueue) {
                        if (al.get(2).equals(failed_port)) {
                            holdBackQueue.remove(c);
                        }
                        c++;
                    }

                    while (holdBackQueue.size() > 0 && holdBackQueue.peek().get(5).equals(DELIVERABLE)) {
                        List<String> popList = holdBackQueue.poll();

                        final String passedMsg = popList.get(1);
                        synchronized (this) {
                            // adding to db
                            ContentResolver contentResolver = getContentResolver();
                            ContentValues values = new ContentValues();
                            values.put("value", passedMsg);

                            int key = msgPrefs.getInt("messagecounter", 0);
                            SharedPreferences.Editor editor = msgPrefs.edit();
                            editor.putInt("messagecounter", key + 1);
                            editor.commit();

                            values.put("key", String.valueOf(key));

                            String providerUrl = "content://edu.buffalo.cse.cse486586.groupmessenger2.provider";
                            Uri providerUri = Uri.parse(providerUrl);
                            Uri uri = contentResolver.insert(providerUri, values);

                            publishProgress(passedMsg);
                        }
                    }
                }
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

            String filename = "SimpleMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            return;
        }
    }

    /**
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko & dheeraj
     * @reference Univ. of Maryland Android Threads, AsyncTasks & Handlers Tutorial: https://www.youtube.com/watch?v=Rh9cgaRw8n4
     * Android AsyncTask Tutorial: https://www.youtube.com/watch?v=V4q0sTIntsk
     * Android Socket Programming Tutorial: https://www.youtube.com/watch?v=ckWG3JXCCzM
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            String localPort = msgs[1];
            System.out.println(">>>>>>>>>>>>>>>" + msgToSend + "-->" + localPort + "<<<<<<<<<<<<<<<<<<<<<<<");
            messageCounter = messageCounter + 1;
            for (String remotePort : REMOTE_PORTS) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    // writing to view
                    // ISIS Algorithm Phase-1
                    // TRANSMIT:   1. MESSAGE_COUNTER; 2. MESSAGE;  3. LOCAL_PORT;  4. REMOTE_PORT; 5. CASE_1
                    String streamText = messageCounter + DELIMITER + msgToSend.replaceAll("\n", "") + DELIMITER + localPort + DELIMITER + remotePort + DELIMITER + "case1";
                    OutputStream outputStream = socket.getOutputStream();
                    PrintWriter printWriter = new PrintWriter(outputStream);
                    printWriter.write(streamText);
                    printWriter.flush();
                    printWriter.close();
                    outputStream.close();
                    socket.close();
                } catch (SocketTimeoutException ste) {
                    Log.e(TAG, "Socket timeout.");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }
            return null;
        }
    }


    private class ISISClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            // Transmit: 1.MESSAGE_COUNTER; 2. SI;  3.SENDER_PORT   4. RECEIVER_PORT
            String msgCounter = msgs[0];
            String si = msgs[1];
            String senderPort = msgs[2];
            String receiverPort = msgs[3];
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(senderPort));
                // writing to view
                // ISIS Algorithm Phase-3
                // TRANSMIT:   1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.SEQUENCE_NUMBER   4. REMOTE_PORT; 5. DELIVERABLE
                String streamText = msgCounter + DELIMITER + senderPort + DELIMITER + si + DELIMITER + receiverPort + DELIMITER + "case2";
                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printWriter = new PrintWriter(outputStream);
                printWriter.write(streamText);
                printWriter.flush();
                printWriter.close();
                outputStream.close();
                socket.close();
            } catch (SocketTimeoutException ste) {
                Log.e(TAG, "Socket timeout.");
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }

    private class MulticastClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            // RECEIVE: 1. MESSAGE_COUNTER; 2. SENDER_PORT;  3.Highest_SEQUENCE_NUMBER   4. REMOTE_PORT;
            String msgCounter = msgs[0];
            String senderPort = msgs[1];
            String si = msgs[2];
            System.out.println("*****Step-3*****");
            for (String remotePort : REMOTE_PORTS) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    // writing to view
                    // ISIS Algorithm Phase-1
                    // TRANSMIT:   1. MESSAGE_COUNTER; 2. SENDER PORT;  3. SI;  4. RECEIVER PORT; 5. CASE_3
                    String streamText = msgCounter + DELIMITER + senderPort + DELIMITER + si + DELIMITER + "case3";
                    OutputStream outputStream = socket.getOutputStream();
                    PrintWriter printWriter = new PrintWriter(outputStream);
                    printWriter.write(streamText);
                    printWriter.flush();
                    printWriter.close();
                    outputStream.close();
                    socket.close();
                } catch (SocketTimeoutException ste) {
                    Log.e(TAG, "Socket timeout.");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }
            return null;
        }
    }

    /*private class AvdDownClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort = msgs[0];
            System.out.println("*****Step-0*****");
            for (String remotePort : REMOTE_PORTS) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    String streamText = senderPort + DELIMITER + "case4";
                    OutputStream outputStream = socket.getOutputStream();
                    PrintWriter printWriter = new PrintWriter(outputStream);
                    printWriter.write(streamText);
                    printWriter.flush();
                    printWriter.close();
                    outputStream.close();
                    socket.close();
                    System.out.println("*****Step-5*****");
                } catch (SocketTimeoutException ste) {
                    Log.e(TAG, "Socket timeout.");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }
            return null;
        }
    }*/
}