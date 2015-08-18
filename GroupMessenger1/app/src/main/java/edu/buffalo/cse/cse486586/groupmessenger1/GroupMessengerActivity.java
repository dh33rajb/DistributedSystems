package edu.buffalo.cse.cse486586.groupmessenger1;

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
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORTS = new String[]{"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;

    public static final String MESSAGE_COUNTER_PREF = "MessageCounterPrefs";
    public SharedPreferences msgPrefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
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

                    String providerUrl = "content://edu.buffalo.cse.cse486586.groupmessenger1.provider";
                    Uri providerUri = Uri.parse(providerUrl);
                    Uri uri = contentResolver.insert(providerUri, values);

                    publishProgress(passedMsg);
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
            for (String remotePort : REMOTE_PORTS) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    // writing to view
                    OutputStream outputStream = socket.getOutputStream();
                    PrintWriter printWriter = new PrintWriter(outputStream);
                    printWriter.write(msgToSend);
                    printWriter.flush();
                    printWriter.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }
            return null;
        }
    }
}
