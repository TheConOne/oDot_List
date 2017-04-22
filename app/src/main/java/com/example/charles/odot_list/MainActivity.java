package com.example.charles.odot_list;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.myscript.atk.core.ui.IStroker;
import com.myscript.atk.scw.SingleCharWidget;
import com.myscript.atk.scw.SingleCharWidgetApi;
import com.myscript.atk.text.CandidateInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        SingleCharWidgetApi.OnConfiguredListener,
        SingleCharWidgetApi.OnTextChangedListener,
        SingleCharWidgetApi.OnSingleTapGestureListener,
        SingleCharWidgetApi.OnLongPressGestureListener,
        SingleCharWidgetApi.OnBackspaceGestureListener,
        SingleCharWidgetApi.OnReturnGestureListener,
        CustomEditText.OnSelectionChanged,
        GestureOverlayView.OnGesturePerformedListener
{
    DBHelper DBHelper;
    ArrayAdapter<String> mAdapter;
    ListView lstTask;

    //Gesture stuff
    int selectedIndex = -1;
    private GestureLibrary gestureLib;

    private static final String TAG = "MainActivity";

    private CustomEditText    mTextField;
    private CandidateAdapter  mCandidateAdapter;
    private LinearLayout mCandidateBar;
    private GridView mCandidatePanel;
    private SingleCharWidgetApi  mWidget;

    private boolean doubleBackToExitPressedOnce;
    private Handler mHandler = new Handler();
    boolean isInAPI = false;
    boolean isInGesture = false;
    boolean isInHome = true;
    boolean hasTutorialOccur;
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            doubleBackToExitPressedOnce = false;
        }
    };

    long time;
    boolean firstTouch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //DB stuff ~~~
        Toast.makeText(this, "Double Tap for Gesture Listener", Toast.LENGTH_SHORT).show();
        setContentView(R.layout.activity_main);

        DBHelper = new DBHelper(this);

        lstTask = (ListView)findViewById(R.id.lstTask);

        loadTaskList();
        //~~~
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
//            MainActivity.super.onBackPressed();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
            return;
        }
        else if(isInHome)
        {
            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

            mHandler.postDelayed(mRunnable, 2000);
        }
        else if(isInAPI)
        {
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            isInHome = true;
            isInAPI = false;
            isInGesture = false;
            super.onBackPressed();
        }
        else
        {
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            isInHome = true;
            isInAPI = false;
            isInGesture = false;
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWidget.dispose();
        if (mHandler != null) { mHandler.removeCallbacks(mRunnable); }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == event.ACTION_DOWN){
            if(firstTouch && (System.currentTimeMillis() - time) <= 300) {
                //do stuff here for double tap
                CallGesture();
                Log.e("** DOUBLE TAP**"," second tap ");
                firstTouch = false;

            } else {
                firstTouch = true;
                time = System.currentTimeMillis();
                Log.e("** SINGLE  TAP**"," First Tap time  "+time);
                return false;
            }
        }
        return true;
    }

    protected void CallGesture(){
        isInGesture = true;
        isInAPI = false;
        isInHome = false;

        setContentView(R.layout.gesture_layout);
        GestureOverlayView gestureOverlayView = new GestureOverlayView(this);
        // Here we are adding gestureOverlay to our UI
        View inflate = getLayoutInflater().inflate(R.layout.gesture_layout, null);
        gestureOverlayView.addView(inflate);
        gestureOverlayView.addOnGesturePerformedListener(this);

        // Initialise gesture lib
        gestureLib = GestureLibraries.fromRawResource(this, R.raw.gestures);
        if (!gestureLib.load()) {
            finish();
        }
        setContentView(gestureOverlayView);
    }

    protected void CallAPI(){
        isInAPI = true;
        isInHome = false;
        isInGesture = false;

        setContentView(R.layout.add_new_task);

        mCandidateAdapter = new CandidateAdapter(this);

        mTextField = (CustomEditText) findViewById(R.id.textField);
        mTextField.setOnSelectionChangedListener(this);

        mCandidateBar = (LinearLayout) findViewById(R.id.candidateBar);
        mCandidatePanel = (GridView) findViewById(R.id.candidatePanel);
        mCandidatePanel.setAdapter(mCandidateAdapter);

        mWidget = (SingleCharWidgetApi) findViewById(R.id.widget);

        if (!mWidget.registerCertificate(MyCertificate.getBytes())) {
            android.app.AlertDialog.Builder dlgAlert  = new android.app.AlertDialog.Builder(this);
            dlgAlert.setMessage("Please use a valid certificate.");
            dlgAlert.setTitle("Invalid certificate");
            dlgAlert.setCancelable(false);
            dlgAlert.setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    //dismiss the dialog
                }
            });
            dlgAlert.create().show();
            return;
        }

        mWidget.setOnConfiguredListener(this);
        mWidget.setOnTextChangedListener(this);
        mWidget.setOnBackspaceGestureListener(this);
        mWidget.setOnReturnGestureListener(this);
        mWidget.setOnSingleTapGestureListener(this);
        mWidget.setOnLongPressGestureListener(this);

        mWidget.addSearchDir("zip://" + getPackageCodePath() + "!/assets/conf");
        mWidget.configure("en_US", "si_text");
    }

    //--------------------------------------------------------------------------------
    // UI callbacks

    private class CandidateTag {
        int start;
        int end;
        String text;
    }

    public void onClearButtonClick(View v) {
        mWidget.clear();
    }

    public void onCandidateButtonClick(View v) {
        CandidateTag tag = (CandidateTag) v.getTag();
        if (tag != null) {
            mWidget.replaceCharacters(tag.start, tag.end, tag.text);
        }
    }

    public void onMoreButtonClick(View v) {
        mCandidatePanel.setVisibility(mCandidatePanel.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        updateCandidatePanel();
    }

    public void onEmoticonButtonClick(View v) {
        mWidget.insertString(":-)");
    }

    public void onSpaceButtonClick(View v) {
        mWidget.insertString(" ");
    }

    public void onDeleteButtonClick(View v) {
        CandidateInfo info = mWidget.getCharacterCandidates(mWidget.getInsertIndex() - 1);
        mWidget.replaceCharacters(info.getStart(), info.getEnd(), null);
    }

    public void onAddButtonClick(View v){
        String task = String.valueOf(mTextField.getText());
        if(task.compareTo("") != 0) {
            DBHelper.insertNewTask(task);
            loadTaskList();
            mWidget.clear();
        }
        //go back to main page
        startActivity(new Intent(MainActivity.this, MainActivity.class));
        isInHome = true;
        isInAPI = false;
        isInGesture = false;
        MainActivity.super.onBackPressed();
    }


    public void onCancelButtonClick(View v){
        mWidget.clear();
        //go back to main page
        startActivity(new Intent(MainActivity.this, MainActivity.class));
        isInHome = true;
        isInAPI = false;
        isInGesture = false;
        MainActivity.super.onBackPressed();
    }

    public void onNewTaskClick(View v){
        CallAPI();
    }


    @Override
    public void onSelectionChanged(EditText editText, int selStart, int selEnd) {
        Log.d(TAG, "Selection changed to [" + selStart + "-" + selEnd + "]");
        mWidget.setInsertIndex(selStart);
        updateCandidateBar();
        updateCandidatePanel();
    }

    //--------------------------------------------------------------------------------
    // Widget callbacks

    @Override
    public void onConfigured(SingleCharWidgetApi w, boolean success) {
        Log.d(TAG, "Widget configuration " + (success ? "done" : "failed"));
    }

    @Override
    public void onTextChanged(SingleCharWidgetApi w, String text, boolean intermediate) {
        Log.d(TAG, "Text changed to \"" + text + "\" (" + (intermediate ? "intermediate" : "stable") + "), insert index=" + mWidget.getInsertIndex());
        // temporarily disable selection changed listener to prevent spurious cursor jumps
        mTextField.setOnSelectionChangedListener(null);
        mTextField.setTextKeepState(text);
        mTextField.setSelection(mWidget.getInsertIndex());
        mTextField.setOnSelectionChangedListener(this);
        updateCandidateBar();
        updateCandidatePanel();
    }

    @Override
    public void onBackspaceGesture(SingleCharWidgetApi w, int index, int count) {
        Log.d(TAG, "Backspace gesture detected at index " + index + " (" + count + ")");
        CandidateInfo info = mWidget.getCharacterCandidates(index - 1);
        mWidget.replaceCharacters(info.getStart(), info.getEnd(), null);
    }

    @Override
    public void onReturnGesture(SingleCharWidgetApi w, int index) {
        Log.d(TAG, "Return gesture detected at index " + index);
        mWidget.replaceCharacters(index, index, "\n");
    }

    @Override
    public boolean onSingleTapGesture(SingleCharWidgetApi w, float x, float y) {
        Log.d(TAG, "Single tap gesture detected at x=" + x + " y=" + y);
        // we don't handle the gesture
        return false;
    }

    @Override
    public boolean onLongPressGesture(SingleCharWidgetApi w, float x, float y) {
        Log.d(TAG, "Long press gesture detected at x=" + x + " y=" + y);
        // we don't handle the gesture
        return false;
    }

    //--------------------------------------------------------------------------------
    // Candidates

    private class CandidateAdapter extends BaseAdapter {

        private Context mContext;
        private List<CandidateTag> mCandidates;

        public CandidateAdapter(Context context) {
            mContext = context;
            mCandidates = Collections.emptyList();
        }

        public void setCandidates(List<CandidateTag> candidates) {
            mCandidates = candidates;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mCandidates.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Button b;
            if (convertView == null) {
                b = new Button(mContext);
                b.setAllCaps(false);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onCandidateButtonClick(v);
                    }
                });
            } else {
                b = (Button) convertView;
            }
            CandidateTag tag = mCandidates.get(position);
            b.setTag(tag);
            b.setText(tag.text.replace("\n", "\u21B2"));
            return b;
        }
    }

    // update candidates bar
    private void updateCandidateBar() {
        int index = mWidget.getInsertIndex() - 1;
        if (index < 0) {
            index = 0;
        }

        CandidateInfo info = mWidget.getWordCandidates(index);

        int start = info.getStart();
        int end = info.getEnd();
        List<String> labels = info.getLabels();
        List<String> completions = info.getCompletions();

        for (int i=0; i<3; i++) {
            Button b = (Button) mCandidateBar.getChildAt(i);

            CandidateTag tag = new CandidateTag();
            if (i < labels.size()) {
                tag.start = start;
                tag.end = end;
                tag.text = labels.get(i) + completions.get(i);
            } else {
                tag = null;
            }

            b.setTag(tag);

            if (tag != null) {
                b.setText(tag.text.replace("\n", "\u21B2"));
            } else {
                b.setText("");
            }
        }
    }

    // update candidates panel
    private void updateCandidatePanel() {
        if (mCandidatePanel.getVisibility() == View.GONE) {
            return;
        }

        List<CandidateTag> candidates = new ArrayList<>();

        int index = mWidget.getInsertIndex() - 1;
        if (index < 0) {
            index = 0;
        }

        CandidateInfo[] infos = {
                // add word-level candidates
                mWidget.getWordCandidates(index),
                // add character-level candidates
                mWidget.getCharacterCandidates(index),
        };

        for (CandidateInfo info : infos) {
            int start = info.getStart();
            int end = info.getEnd();
            List<String> labels = info.getLabels();
            List<String> completions = info.getCompletions();

            for (int i=0; i<labels.size(); i++) {
                CandidateTag tag = new CandidateTag();
                tag.start = start;
                tag.end = end;
                tag.text = labels.get(i) + completions.get(i);
                candidates.add(tag);
            }
        }

        if (candidates.isEmpty()) {
            mCandidatePanel.setVisibility(View.GONE);
        }

        mCandidateAdapter.setCandidates(candidates);
    }
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState)
//    {
//        super.onCreate(savedInstanceState);
//
//        //DB stuff ~~~
//        setContentView(R.layout.activity_main);
//
//        DBHelper = new DBHelper(this);
//
//        lstTask = (ListView)findViewById(R.id.lstTask);
//
//        loadTaskList();
//        //~~~
//
//        setContentView(R.layout.add_new_task);
//
//        widget = (SingleCharWidget) findViewById(R.id.singleChar_widget);
//        if (!widget.registerCertificate(MyCertificate.getBytes()))
//        {
//            AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
//            dlgAlert.setMessage("Please use a valid certificate.");
//            dlgAlert.setTitle("Invalid certificate");
//            dlgAlert.setCancelable(false);
//            dlgAlert.setPositiveButton("OK", new DialogInterface.OnClickListener()
//            {
//                public void onClick(DialogInterface dialog, int which)
//                {
//                    //dismiss the dialog
//                }
//            });
//            dlgAlert.create().show();
//            return;
//        }
//
//        widget.setOnConfiguredListener(this);
//        widget.setOnTextChangedListener(this);
//
//        // references assets directly from the APK to avoid extraction in application
//        // file system
//        widget.addSearchDir("zip://" + getPackageCodePath() + "!/assets/conf");
//
//        // The configuration is an asynchronous operation. Callbacks are provided to
//        // monitor the beginning and end of the configuration process and update the UI
//        // of the input method accordingly.
//        //
//        // "en_US" references the en_US bundle name in conf/en_US.conf file in your assets.
//        // "si_text" references the configuration name in en_US.conf
//        widget.configure("en_US", "si_text");
//    }
//
//    @Override
//    protected void onDestroy()
//    {
//        widget.setOnTextChangedListener(null);
//        widget.setOnConfiguredListener(null);
//
//        super.onDestroy();
//    }
//
//    @Override
//    public void onConfigured(SingleCharWidgetApi widget, boolean success)
//    {
//        if(!success)
//        {
//            Toast.makeText(getApplicationContext(), widget.getErrorString(), Toast.LENGTH_LONG).show();
//            Log.e(TAG, "Unable to configure the Single Char Widget: " + widget.getErrorString());
//            return;
//        }
//        Toast.makeText(getApplicationContext(), "Single Char Widget Configured", Toast.LENGTH_SHORT).show();
//        if(BuildConfig.DEBUG)
//            Log.d(TAG, "Single Char Widget configured!");
//    }
//
//    @Override
//    public void onTextChanged(SingleCharWidgetApi widget, String s, boolean intermediate)
//    {
//        Toast.makeText(getApplicationContext(), "Recognition update", Toast.LENGTH_SHORT).show();
//        if(BuildConfig.DEBUG)
//        {
//            Log.d(TAG, "Single Char Widget recognition: " + widget.getText());
//        }
//    }


    private void loadTaskList() {
        ArrayList<String> taskList = DBHelper.getTaskList();
        if(mAdapter==null){
            mAdapter = new ArrayAdapter<String>(this,R.layout.row,R.id.task_title,taskList);
            lstTask.setAdapter(mAdapter);
        }
        else{
            mAdapter.clear();
            mAdapter.addAll(taskList);
            mAdapter.notifyDataSetChanged();
        }
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.menu,menu);
//
//        //Change menu icon color
//        Drawable icon = menu.getItem(0).getIcon();
//        icon.mutate();
//        icon.setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_IN);
//
//        return super.onCreateOptionsMenu(menu);
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()){
//            case R.id.action_add_task:
//                final CustomEditText taskEditText = new CustomEditText(this);
//                //API stuff~~~~~
//                CallAPI();
//                Toast.makeText(this, "This be the thing", Toast.LENGTH_SHORT).show();
//                return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    public void deleteTask(View view){
        View parent = (View)view.getParent();
        TextView taskTextView = (TextView)parent.findViewById(R.id.task_title);
        Log.e("String", (String) taskTextView.getText());
        String task = String.valueOf(taskTextView.getText());
        DBHelper.deleteTask(task);
        loadTaskList();
    }

    //Gesture stuff
    //```````````````````````````````````````````````````````````````````````
    @Override
    public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
        ArrayList<Prediction> predictions = gestureLib.recognize(gesture);
        for (Prediction prediction : predictions) {
//            Toast.makeText(this, prediction.name+" : "+prediction.score, Toast.LENGTH_SHORT).show();
            if (prediction.name.equals("Add")) {
                if (prediction.score > 3.0) {
//                    Toast.makeText(this, prediction.name+" : "+prediction.score, Toast.LENGTH_SHORT).show();
                    CallAPI();
                }
            }
            else if (prediction.name.equals("Create")) {
                if (prediction.score > 3.0) {
//                    Toast.makeText(this, prediction.name+" : "+prediction.score, Toast.LENGTH_SHORT).show();
                    CallAPI();
                }
            }
            else if (prediction.name.equals("Show")) {
                if (prediction.score > 2.0) {
//                    Toast.makeText(this, prediction.name+" : "+prediction.score, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, MainActivity.class));
                    isInHome = true;
                    isInAPI = false;
                    isInGesture = false;
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
            else if (prediction.name.equals("Delete")) {
                if (prediction.score > 1.5){
//                    Toast.makeText(this, prediction.name+" All : "+prediction.score, Toast.LENGTH_SHORT).show();
                        DBHelper.deleteAll();

                        startActivity(new Intent(MainActivity.this, MainActivity.class));
                        isInHome = true;
                        isInAPI = false;
                        isInGesture = false;
                        android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        }
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        selectedIndex = info.position;

        return true;
    }


    public void showMsg(String title, String msg) {
        android.app.AlertDialog.Builder build = new android.app.AlertDialog.Builder(this);
        build.setCancelable(true);
        build.setTitle(title);
        build.setMessage(msg);
        build.show();

    }

}