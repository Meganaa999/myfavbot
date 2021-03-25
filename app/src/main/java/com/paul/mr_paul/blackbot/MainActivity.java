package com.paul.mr_paul.blackbot;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.paul.mr_paul.blackbot.Adapters.MessageAdapter;
import com.paul.mr_paul.blackbot.Contract.MessageContract;
import com.paul.mr_paul.blackbot.DBHelper.MessageDBHelper;
import com.paul.mr_paul.blackbot.DataTypes.MessageData;
import com.paul.mr_paul.blackbot.UtilityPackage.Constants;
import com.paul.mr_paul.blackbot.Contract.VariableContract;
import com.paul.mr_paul.blackbot.DBHelper.VariableDBHelper;
import com.paul.mr_paul.blackbot.DataTypes.VariableData;

import com.bumptech.glide.Glide;

import org.alicebot.ab.AIMLProcessor;
import org.alicebot.ab.Bot;
import org.alicebot.ab.Chat;
import org.alicebot.ab.Graphmaster;
import org.alicebot.ab.MagicBooleans;
import org.alicebot.ab.MagicStrings;
import org.alicebot.ab.PCAIMLProcessorExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;



public class MainActivity extends AppCompatActivity {

    private EditText messageInputView;
    private ImageView botWritingView;
    private int timePerCharacter;
    private ImageView botSpeechToggle;

    public Bot bot;
    public static Chat chat;
    private boolean speechAllowed; // the flag for toggling speech engine

    private TextToSpeech textToSpeech;

    private SQLiteDatabase database;
    private SQLiteDatabase databasevar;
    private MessageAdapter messageAdapter;

    SharedPreferences preferences;

    private BottomSheetBehavior bottomSheetBehavior;
    //StringBuilder name = new StringBuilder();
    StringBuilder address = new StringBuilder();
    //static String name;
    //static String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MessageDBHelper messageDBHelper = new MessageDBHelper(this);
        database = messageDBHelper.getWritableDatabase();

        VariableDBHelper variableDBHelper = new VariableDBHelper(this);
        databasevar = variableDBHelper.getWritableDatabase();


        timePerCharacter = 30 + (new Random().nextInt(30)); // 30 - 60

        messageInputView = findViewById(R.id.message_input_view);
        ImageView messageSendButton = findViewById(R.id.message_send_button);
        botWritingView = findViewById(R.id.bot_writing_view);
        final ImageView deleteChatMessages = findViewById(R.id.delete_chats);
        botSpeechToggle = findViewById(R.id.bot_speech_toggle);

        View bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        messageAdapter = new MessageAdapter(this, getAllMessages());
        recyclerView.setAdapter(messageAdapter);

        AssetManager assetManager = getResources().getAssets();
        File cacheDirectory = new File(getCacheDir().toString() + "/mr_paul/bots/darkbot");
        boolean dirMakingSuccessful = cacheDirectory.mkdirs();

        // saving the bot's core data in the cache
        if(dirMakingSuccessful && cacheDirectory.exists()){
            try{
                for(String dir : assetManager.list("darkbot")){
                    File subDirectory = new File(cacheDirectory.getPath() + "/" + dir);
                    subDirectory.mkdirs();
                    for(String file : assetManager.list("darkbot/" + dir)){
                        File f = new File(cacheDirectory.getPath() + "/" + dir + "/" + file);
                        if(!f.exists()){
                            InputStream in;
                            OutputStream out;

                            in = assetManager.open("darkbot/" + dir + "/" + file);
                            out = new FileOutputStream(cacheDirectory.getPath() + "/" + dir + "/" + file);

                            copyFile(in, out);
                            in.close();
                            out.flush();
                            out.close();
                        }
                    }
                }

            } catch(IOException e){
                e.printStackTrace();
                Log.i("darkbot", "IOException occurred when writing from cache!");
            } catch(NullPointerException e){
                Log.i("darkbot", "Nullpoint Exception!");
            }
        }

        // asking for permission for placing call
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE}, 0x12345);
            }
        }


        final ProgressDialog pd = new ProgressDialog(MainActivity.this);
        pd.setTitle("Please Wait");
        pd.setMessage("Initializing Bot...");
        pd.setCanceledOnTouchOutside(false);
        pd.setCancelable(false);

        // handler for communication with the background thread
        final Handler handler = new Handler(){
            @Override
            public void dispatchMessage(Message msg) {
                super.dispatchMessage(msg);
                pd.cancel();
            }
        };

        // initializing the bot in background thread
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                MagicStrings.root_path = getCacheDir().toString() + "/mr_paul";
                AIMLProcessor.extension = new PCAIMLProcessorExtension();
                bot = new Bot("darkbot", MagicStrings.root_path, "chat");
                chat = new Chat(bot);
                handler.sendMessage(new Message()); // dispatch a message to the UI thread
            }
        });

        // finally show the progress dialog box and start the thread
        pd.show();
        thread.start();

        // listen for button click
        messageSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendChatMessage();
            }
        });

        // initialization of speech engine
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS){
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                        Toast.makeText(MainActivity.this,
                                "Default Language not recognized!", Toast.LENGTH_SHORT).show();
                        Log.i("darkbot", "Speech Engine not initialized");
                    } else{
                        preferences = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
                        /*Initially keep speech turned off*/
                        Boolean wasSpeechAllowed = preferences.getBoolean(Constants.WAS_SPEECH_ALLOWED, false);
                        speechAllowed = wasSpeechAllowed;

                        if(wasSpeechAllowed){
                            // show the mute button
                            botSpeechToggle.setImageResource(R.drawable.ic_mute_button);

                        } else{
                            // show the volume up button
                            botSpeechToggle.setImageResource(R.drawable.ic_volume_up_button);
                        }

                    }
                }
            }
        });

        deleteChatMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllChatData();
            }
        });

        botSpeechToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(speechAllowed){
                    speechAllowed = false;
                    // show the volume up button - currently the bot is mute
                    botSpeechToggle.setImageResource(R.drawable.ic_volume_up_button);
                } else{
                    speechAllowed = true;
                    // show the mute button - currently the bot is speaking
                    botSpeechToggle.setImageResource(R.drawable.ic_mute_button);
                }

                // finally write the settings to the shared preference
                preferences.edit().putBoolean(Constants.WAS_SPEECH_ALLOWED, speechAllowed).apply();

            }
        });

        // delete a particular message by just swiping right
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
                removeItem((long) viewHolder.itemView.getTag());
            }
        }).attachToRecyclerView(recyclerView);

        // about project show section

        String about = "Paul's Black Bot\n\nAn AIML based chat bot, which wholeheartedly listens to whatever you have to say it. " +
                "Can place call from your contact, launch any app on your Android Device, " +
                "or can discuss with you about LIFE, UNIVERSE and EVERYTHING.\n\n" +
                "This app was my genuine attempt to make a bot like Natasha (Hike), which also uses" +
                " AIML (Artificial Intelligence Markup Language), which makes uses of wildcards to match certain " +
                "string patterns and produces responsive replies in the form of the hard-coded template.\n" +
                "So, nothing impressive!\n\n" +
                "You can click on this text to view the github repo of this project.\n\n\nJyotirmoy Paul";

        TextView aboutBlackBot = findViewById(R.id.about_black_bot);
        aboutBlackBot.setText(about);

        TextView description = findViewById(R.id.show_description);
        description.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        aboutBlackBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/jyotirmoy-paul/BlackBot"));
                startActivity(intent);
            }
        });

    }

    // method to delete a single chat message
    private void removeItem(long id){
        database.delete(MessageContract.MessageEntry.TABLE_NAME,
                MessageContract.MessageEntry._ID + "=" + id,null);
        messageAdapter.swapCursor(getAllMessages());
    }

    // method to delete all the chat data
    private void deleteAllChatData(){
        // ask for user confirmation
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Are you sure, You want to delete all the chats?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                database.execSQL("DELETE FROM " + MessageContract.MessageEntry.TABLE_NAME);
                messageAdapter.swapCursor(getAllMessages());

                Toast.makeText(MainActivity.this,
                        "All chats deleted!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Nopes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
    //delete variable data
    private void deleteAllVariableData(){
        // ask for user confirmation
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Are you sure, You want to delete all the patient details?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                databasevar.execSQL("DELETE FROM " + VariableContract.VariableEntry.TABLE_NAME);
                //messageAdapter.swapCursor(getAllMessages());

                Toast.makeText(MainActivity.this,
                        "All previous details deleted!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Nopes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    private Cursor getAllMessages(){
        return database.query(
                MessageContract.MessageEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                MessageContract.MessageEntry._ID + " DESC"
        );
    }
    private Cursor getAnyVariable(String y){
        //String dataname=VariableContract.VariableEntry.TABLE_NAME;
        //Cursor res =  databasevar.rawQuery( "select * from variable_data where id=x" , null );
        //return res;(x)
        //String y=Integer.toString(x);
        return databasevar.query(
                VariableContract.VariableEntry.TABLE_NAME,
                new String[]{"value"},
                "variable=?",
                new String[]{y},
                null,
                null,
                null
        );
    }


    // message sending method
    public void sendChatMessage(){
        String message = messageInputView.getText().toString().trim();
        if(message.isEmpty()){
            messageInputView.setError("Can't send empty message!");
            messageInputView.requestFocus();
            return;
        }

        DateFormat dateFormat = new SimpleDateFormat("hh:mm dd/MM/yyyy");
        String timeStamp = dateFormat.format(new Date());

        addMessage(new MessageData(Constants.USER, message, timeStamp));

        if(message.toUpperCase().startsWith("CALL")){
            // calling a phone number as requested by user

            String[] replyForCalling = {
                    "Calling",
                    "Placing a call on",
                    "Definitely, calling",
                    "There we go, calling",
                    "Making a call to",
                    "Ji sir, calling"
            };

            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForCalling[new Random().nextInt(replyForCalling.length)] + " " + temp[1], timeStamp));
            Cursor result=getAnyVariable("number");
            result.moveToFirst();
            String ans=result.getString(result.getColumnIndex("value"));



            makeCall(ans);
        } else if(message.toUpperCase().startsWith("OPEN") || message.toUpperCase().startsWith("LAUNCH")){
            // call intent to app, requested by user

            String[] replyForOpeningApp = {
                    "There we go, opening",
                    "Launching",
                    "Opening",
                    "Trying to open",
                    "Trying to launch",
                    "There we go, launching"
            };

            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForOpeningApp[new Random().nextInt(replyForOpeningApp.length)] + " " + temp[1],timeStamp));
            if(temp[1].equals("maps"))
            {
                launchApp(temp[1]);
            }
            else
                launchApp(getAppName(temp[1]));
        } else if(message.toUpperCase().startsWith("DELETE") || message.toUpperCase().startsWith("CLEAR")){
            displayBotReply(new MessageData(Constants.BOT,"Okay! I will clear up everything for you!", timeStamp));
        } else if(message.toUpperCase().contains("JOKE")){

            String[] replyForJokes = {
                    "Jokes coming right up...",
                    "Processing a hot'n'fresh joke, right for you!",
                    "There you go...",
                    "This might make you laugh...",
                    "My jokes are still in alpha, Hopefully soon they'll get beta, till then...",
                    "Jokes are my another speciality, there you go...",
                    "Jokes, you ask? This might make you laugh...",
                    "Trying to make you laugh...",
                    "You might find this funny...",
                    "Enjoy your joke..."
            };

            displayBotReply(new MessageData(Constants.BOT, replyForJokes[new Random().nextInt(replyForJokes.length)] + "\n" + mainFunction(message), timeStamp));

        }
        else if(message.toUpperCase().startsWith("NAME"))
        {
            // calling a phone number as requested by user

            //String replyForEntering ="Noting Patient's details";
            //String[] temp = message.split(" ", 2);
            //displayBotReply(new MessageData(Constants.BOT, replyForEntering,timeStamp));
            String[] replyForEntering = {
                    "Storing name",
                    "Saving name"
            };

            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForEntering[new Random().nextInt(replyForEntering.length)] + " " + temp[1],timeStamp));
            //if(temp[1].toUpperCase().equals("NAME")){
                //address.append(temp[1]);
            DateFormat dateFormatVar = new SimpleDateFormat("hh:mm dd/MM/yyyy");
            String timeStampVar = dateFormatVar.format(new Date());

            addVariable(new VariableData(Constants.NAME,temp[1], timeStampVar));
            //}
            //else if(temp[1].toUpperCase().equals("ADDRESS")){
                //address.append(temp[3]);
            //}
        }
        else if(message.toUpperCase().startsWith("ADDRESS"))
        {
            // calling a phone number as requested by user

            //String replyForEntering ="Noting Patient's details";
            //String[] temp = message.split(" ", 2);
            //displayBotReply(new MessageData(Constants.BOT, replyForEntering,timeStamp));
            String[] replyForEntering = {
                    "Storing address",
                    "Saving address"
            };
            //address victorial memorial
            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForEntering[new Random().nextInt(replyForEntering.length)] + " " + temp[1],timeStamp));
            //if(temp[1].toUpperCase().equals("NAME")){
            //address.append(temp[1]);
            DateFormat dateFormatVar = new SimpleDateFormat("hh:mm dd/MM/yyyy");
            String timeStampVar = dateFormatVar.format(new Date());

            addVariable(new VariableData(Constants.ADDRESS,temp[1], timeStampVar));
            //}
            //else if(temp[1].toUpperCase().equals("ADDRESS")){
            //address.append(temp[3]);
            //}
        }
        else if(message.toUpperCase().startsWith("NUMBER"))
        {
            // calling a phone number as requested by user

            //String replyForEntering ="Noting Patient's details";
            //String[] temp = message.split(" ", 2);
            //displayBotReply(new MessageData(Constants.BOT, replyForEntering,timeStamp));
            String[] replyForEntering = {
                    "Storing number",
                    "Saving number"
            };
            //address victorial memorial
            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForEntering[new Random().nextInt(replyForEntering.length)] + " " + temp[1],timeStamp));
            //if(temp[1].toUpperCase().equals("NAME")){
            //address.append(temp[1]);
            DateFormat dateFormatVar = new SimpleDateFormat("hh:mm dd/MM/yyyy");
            String timeStampVar = dateFormatVar.format(new Date());

            addVariable(new VariableData(Constants.NUMBER,temp[1], timeStampVar));
            //}
            //else if(temp[1].toUpperCase().equals("ADDRESS")){
            //address.append(temp[3]);
            //}
        }
        else if(message.toUpperCase().startsWith("RELATION"))
        {
            // calling a phone number as requested by user

            //String replyForEntering ="Noting Patient's details";
            //String[] temp = message.split(" ", 2);
            //displayBotReply(new MessageData(Constants.BOT, replyForEntering,timeStamp));
            String[] replyForEntering = {
                    "Storing family relation",
                    "Saving family relation"
            };
            //address victorial memorial
            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForEntering[new Random().nextInt(replyForEntering.length)] + " " + temp[1],timeStamp));
            //if(temp[1].toUpperCase().equals("NAME")){
            //address.append(temp[1]);
            DateFormat dateFormatVar = new SimpleDateFormat("hh:mm dd/MM/yyyy");
            String timeStampVar = dateFormatVar.format(new Date());

            addVariable(new VariableData(Constants.RELATION,temp[1], timeStampVar));
            //}
            //else if(temp[1].toUpperCase().equals("ADDRESS")){
            //address.append(temp[3]);
            //}
        }
        else if(message.toUpperCase().equals("WHAT IS MY NAME")){
            //String[] temp = message.split(" ");
            //String reply=null;
            //if(temp[3].toUpperCase().equals("NAME")){
            Cursor result=getAnyVariable("name");
            result.moveToFirst();
            String ans=result.getString(result.getColumnIndex("value"));
            String[] replyForWhat ={
                    "my name is "+ans,
                    "hello "+ans
            };
            //}
            //else if(temp[3].toUpperCase().startsWith("ADDRESS")){
                //reply = "my" + temp[3] + "is" + address;
            //}

            displayBotReply(new MessageData(Constants.BOT, replyForWhat[new Random().nextInt(replyForWhat.length)]+" ",timeStamp));

        }
        else if(message.toUpperCase().equals("WHERE DO I LIVE")){
            //String[] temp = message.split(" ");
            //String reply=null;
            //if(temp[3].toUpperCase().equals("NAME")){
            Cursor result=getAnyVariable("address");
            result.moveToFirst();
            String ans=result.getString(result.getColumnIndex("value"));
            String[] replyForWhat ={
                    "your home is at "+ans,
                    "you live at "+ans
            };
            //}
            //else if(temp[3].toUpperCase().startsWith("ADDRESS")){
            //reply = "my" + temp[3] + "is" + address;
            //}

            displayBotReply(new MessageData(Constants.BOT, replyForWhat[new Random().nextInt(replyForWhat.length)]+" ",timeStamp));

        }
        else if(message.toUpperCase().equals("NEW USER")){
            deleteAllVariableData();
        }
        else{
            // chat with bot - save the reply from the bot
            String botReply = mainFunction(message);
            if(botReply.trim().isEmpty()){
                botReply = mainFunction("UDC");
            }
            displayBotReply(new MessageData(Constants.BOT, botReply,timeStamp));
        }

        messageInputView.setText("");

    }

    // displayBotReply() method
    private void displayBotReply(final MessageData messageData){

        botWritingView.setVisibility(View.VISIBLE);
        Glide.with(MainActivity.this).asGif().load(R.drawable.bot_animation).into(botWritingView);

        final String message = messageData.getMessage();
        int lengthOfMessage = message.length();

        int timeToWriteInMillis = lengthOfMessage*timePerCharacter; // each character taking 10ms to write
        if(timeToWriteInMillis > 3000){timeToWriteInMillis = 3000;} // not letting go beyond 3 secs

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                botWritingView.setVisibility(View.GONE);

                addMessage(messageData);

                if(messageData.getMessage().equals("Okay! I will clear up everything for you!")){
                    // the user requested to delete all chat data
                    deleteAllChatData();
                }

                // speak out the bot reply
                if(speechAllowed){
                    textToSpeech.setSpeechRate(0.9f);
                    textToSpeech.setPitch(1f);

                    textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null);
                }

            }
        }, timeToWriteInMillis); // the delay is according to the length of message

    }

    private void addMessage(MessageData messageData){
        String sender = messageData.getSender();
        String message = messageData.getMessage();
        String timestamp = messageData.getTimeStamp();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MessageContract.MessageEntry.COLUMN_SENDER, sender);
        contentValues.put(MessageContract.MessageEntry.COLUMN_MESSAGE, message);
        contentValues.put(MessageContract.MessageEntry.COLUMN_TIMESTAMP, timestamp);

        database.insert(MessageContract.MessageEntry.TABLE_NAME, null, contentValues);
        messageAdapter.swapCursor(getAllMessages());
    }
    private void addVariable(VariableData variableData){
        String var = variableData.getVariable();
        String value = variableData.getValue();
        String timestamp = variableData.getTimeStamp();

        ContentValues contentValuesVar = new ContentValues();
        contentValuesVar.put(VariableContract.VariableEntry.COLUMN_VAR, var);
        contentValuesVar.put(VariableContract.VariableEntry.COLUMN_VALUE, value);
        contentValuesVar.put(VariableContract.VariableEntry.COLUMN_TIMESTAMP, timestamp);

        databasevar.insert(VariableContract.VariableEntry.TABLE_NAME, null, contentValuesVar);
        //messageAdapter.swapCursor(getAllMessages());
    }

    // UTILITY METHODS

    // copying the file
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    // responding of bot to user's requests
    public static String mainFunction (String args) {

        MagicBooleans.trace_mode = false;
        Graphmaster.enableShortCuts = true;

        return chat.multisentenceRespond(args);
    }

    // functionality of the bot

    // method for searching a name in user's contact list
    public String getNumber(String name, Context context){

        String number = "";
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[] {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};

        Cursor people = context.getContentResolver().query(uri, projection, null, null, null);
        if(people == null){
            return number;
        }

        int indexName = people.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        int indexNumber = people.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

        people.moveToFirst();
        do {
            String Name   = people.getString(indexName);
            String Number = people.getString(indexNumber);
            if(Name.equalsIgnoreCase(name)){return Number.replace("-", "");}
        } while (people.moveToNext());

        people.close();

        return number;
    }

    // method for placing a call
    private void makeCall(String name){


        try {
            String number;

            if(name.matches("[0-9]+") && name.length() > 2){
                // string only contains number
                number = name;
            } else{
                number = getNumber(name,MainActivity.this);
            }


            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            startActivity(callIntent);
        }catch (SecurityException e){
            Toast.makeText(this,"Calling Permission - DENIED!",Toast.LENGTH_SHORT).show();
        }
    }

    // method for searching through all the apps in the user's phone
    public String getAppName(String name) {
        name = name.toLowerCase();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> l = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo ai : l) {
            String n = pm.getApplicationLabel(ai).toString().toLowerCase();
            if (n.contains(name) || name.contains(n)){
                return ai.packageName;
            }
        }

        return "package.not.found";
    }

    // method for launching an app
    protected void launchApp(String packageName) {
        if(packageName.equals("maps")){
            Log.i("darkbot","in the func maps");
            Cursor result=getAnyVariable("address");
            result.moveToFirst();
            String ans=result.getString(result.getColumnIndex("value"));
            String url = "http://maps.google.com/maps?daddr="+ans;
            Intent intent = new Intent(android.content.Intent.ACTION_VIEW,  Uri.parse(url));
            startActivity(intent);

        }
        /*else {
            Log.i("darkbot","NOt in the func maps");
            Intent mIntent = getPackageManager().getLaunchIntentForPackage(packageName);

            if (packageName.equals("package.not.found")) {
                Toast.makeText(getApplicationContext(), "I'm afraid, there's no such app!", Toast.LENGTH_SHORT).show();
            } else if (mIntent != null) {
                try {
                    startActivity(mIntent);
                } catch (Exception err) {
                    Log.i("darkbot", "App launch failed!");
                    Toast.makeText(this, "I'm afraid, there's no such app!", Toast.LENGTH_SHORT).show();
                }
            }
        }*/
    }

}
