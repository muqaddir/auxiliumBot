package com.example.vmac.WatBot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.assistant.v2.Assistant;
import com.ibm.watson.developer_cloud.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageInput;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageOptions;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageResponse;
import com.ibm.watson.developer_cloud.assistant.v2.model.SessionResponse;
import com.ibm.watson.developer_cloud.http.ServiceCall;
import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.SynthesizeOptions;


import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;

import java.util.List;



import org.json.JSONArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

  private TextView txvResult;
  private RecyclerView recyclerView;
  private ChatAdapter mAdapter;
  private ArrayList messageArrayList;
  private EditText inputMessage;
  private ImageButton btnSend;
  private ImageButton btnRecord;
  StreamPlayer streamPlayer = new StreamPlayer();
  private boolean initialRequest;
  private boolean permissionToRecordAccepted = false;
  private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
  private static String TAG = "MainActivity";
  private static final int RECORD_REQUEST_CODE = 101;
  private boolean listening = false;
  private MicrophoneInputStream capture;
  private Context mContext;
  private MicrophoneHelper microphoneHelper;

  private Assistant watsonAssistant;
  private SessionResponse watsonAssistantSession;
  private SpeechToText speechService;
  private TextToSpeech textToSpeech;

  private void createServices() {
    watsonAssistant = new Assistant("2018-11-08", new IamOptions.Builder()
            .apiKey(mContext.getString(R.string.assistant_apikey))
            .build());
    watsonAssistant.setEndPoint(mContext.getString(R.string.assistant_url));

    textToSpeech = new TextToSpeech();
    textToSpeech.setIamCredentials(new IamOptions.Builder()
            .apiKey(mContext.getString(R.string.TTS_apikey))
            .build());
    textToSpeech.setEndPoint(mContext.getString(R.string.TTS_url));

    speechService = new SpeechToText();
    speechService.setIamCredentials(new IamOptions.Builder()
            .apiKey(mContext.getString(R.string.STT_apikey))
            .build());
    speechService.setEndPoint(mContext.getString(R.string.STT_url));
  }




  @Override
  protected void onCreate(Bundle savedInstanceState)  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mContext = getApplicationContext();

    inputMessage = findViewById(R.id.message);
    btnSend = findViewById(R.id.btn_send);
    btnRecord = findViewById(R.id.btn_record);
    String customFont = "Montserrat-Regular.ttf";
    Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
    inputMessage.setTypeface(typeface);
    recyclerView = findViewById(R.id.recycler_view);

    messageArrayList = new ArrayList<>();
    mAdapter = new ChatAdapter(messageArrayList);
    microphoneHelper = new MicrophoneHelper(this);

    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    layoutManager.setStackFromEnd(true);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setItemAnimator(new DefaultItemAnimator());
    recyclerView.setAdapter(mAdapter);
    this.inputMessage.setText("");
    this.initialRequest = true;


    int permission = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO);

    if (permission != PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Permission to record denied");
      makeRequest();
    } else {
      Log.i(TAG, "Permission to record was already granted");
    }


    recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), recyclerView, new ClickListener() {
      @Override
      public void onClick(View view, final int position) {
        Message audioMessage = (Message) messageArrayList.get(position);
        if (audioMessage != null && !audioMessage.getMessage().isEmpty()) {
          new SayTask().execute(audioMessage.getMessage());
        }
      }

      @Override
      public void onLongClick(View view, int position) {
        recordMessage();

      }
    }));

    btnSend.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (checkInternetConnection()) {
          sendMessage();
        }
      }
    });

    btnRecord.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        recordMessage();
      }
    });

    createServices();
    sendMessage();
  }

  ;

  // Speech-to-Text Record Audio permission
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case REQUEST_RECORD_AUDIO_PERMISSION:
        permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        break;
      case RECORD_REQUEST_CODE: {

        if (grantResults.length == 0
                || grantResults[0] !=
                PackageManager.PERMISSION_GRANTED) {

          Log.i(TAG, "Permission has been denied by user");
        } else {
          Log.i(TAG, "Permission has been granted by user");
        }
        return;
      }

      case MicrophoneHelper.REQUEST_PERMISSION: {
        if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
        }
      }
    }
    // if (!permissionToRecordAccepted ) finish();

  }

  protected void makeRequest() {
    ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            MicrophoneHelper.REQUEST_PERMISSION);
  }

  // Sending a message to Watson Assistant Service
  private void sendMessage() {

    final String inputmessage = this.inputMessage.getText().toString().trim();
    if (!this.initialRequest) {
      Message inputMessage = new Message();
      inputMessage.setMessage(inputmessage);
      inputMessage.setId("1");
      messageArrayList.add(inputMessage);
    } else {
      Message inputMessage = new Message();
      inputMessage.setMessage(inputmessage);
      inputMessage.setId("100");
      this.initialRequest = false;
      Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();

    }

    this.inputMessage.setText("");
    mAdapter.notifyDataSetChanged();

    Thread thread = new Thread(new Runnable() {
      public void run() {
        try {
          if (watsonAssistantSession == null) {
            ServiceCall<SessionResponse> call = watsonAssistant.createSession(new CreateSessionOptions.Builder().assistantId(mContext.getString(R.string.assistant_id)).build());
            watsonAssistantSession = call.execute();
          }

          MessageInput input = new MessageInput.Builder()
                  .text(inputmessage)
                  .build();
          MessageOptions options = new MessageOptions.Builder()
                  .assistantId(mContext.getString(R.string.assistant_id))
                  .input(input)
                  .sessionId(watsonAssistantSession.getSessionId())
                  .build();
          MessageResponse response = watsonAssistant.message(options).execute();
          Log.i(TAG, "run: "+response);

          if (response != null &&
                  response.getOutput() != null &&
                  !response.getOutput().getGeneric().isEmpty() ){

            JSONArray array = new JSONArray(response.getOutput().getGeneric());
            String str;
            str = new String();
            for (int i=0;i<array.length();i++) {
              final Message outMessage = new Message();
              str = response.getOutput().getGeneric().get(i).getResponseType();


              if ("text".equals(str)) {
                outMessage.setMessage(response.getOutput().getGeneric().get(i).getText());
                outMessage.setId("4");
                messageArrayList.add(outMessage);
                System.out.println("Arr len" + array.length());
              }
              else if ("option".equals(str)) {
                outMessage.setMessage(response.getOutput().getGeneric().get(i).getTitle());
                outMessage.setId("4");
                messageArrayList.add(outMessage);
                JSONArray arrayOptions = new JSONArray(response.getOutput().getGeneric().get(i).getOptions());
                int j = 0;
                while (j < arrayOptions.length()) {
                  final Message outMessage2 = new Message();
                  outMessage2.setMessage(response.getOutput().getGeneric().get(i).getOptions().get(j).getLabel());
                  outMessage2.setId("2");
                  System.out.println(outMessage2);
                  messageArrayList.add(outMessage2);
                  j++;
                }
              }
              else if ("image".equals(str)) {
                ImageView imageView;
                imageView = (ImageView)findViewById(R.id.custom);
                final Message outMessage3 = new Message();
                outMessage3.setMessage(response.getOutput().getGeneric().get(i).getSource());
                outMessage3.setId("2");
                System.out.println(outMessage3);
                messageArrayList.add(outMessage3);
//                imageView.setImageResource(R.drawable.app_logo);
//                messageArrayList.add(imageView);
//                URL url =new URL(response.getOutput().getGeneric().get(i).getSource());
//                Bitmap bmp= BitmapFactory.decodeStream(url.openConnection().getInputStream());
//                imageView.setImageBitmap(bmp);
//                System.out.println("Image set successfully" + url.toString());
              }
              else if("pause".equals(str)) {
//                final Message outMessage4 = new Message();
                long xyz;
                xyz=response.getOutput().getGeneric().get(i).getTime();
                Thread.sleep(xyz);
                //int d=(int)xyz;
                //(outMessage4.setMessage((String.valueOf(response.getOutput().getGeneric().get(i).getTime())));
//                outMessage4.setId("2");
//                System.out.println(outMessage4);
//                messageArrayList.add(outMessage4);
              }
            }
            //int listsize=messageArrayList.size();
            //System.out.println(listsize);
            // speak the message
            //new SayTask().execute(outMessage.getMessage());
            runOnUiThread(new Runnable() {
              public void run() {
                mAdapter.notifyDataSetChanged();
                if (mAdapter.getItemCount() > 1) {
                  recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount() - 1);

                }

              }
            });
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });

    thread.start();

  }

  private class SayTask extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... params) {
      streamPlayer.playStream(textToSpeech.synthesize(new SynthesizeOptions.Builder()
              .text(params[0])
              .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)
              .accept(SynthesizeOptions.Accept.AUDIO_WAV)
              .build()).execute());
      return "Did synthesize";
    }
  }

  //Record a message via Watson Speech to Text
  private void recordMessage() {
    if (listening != true) {
      capture = microphoneHelper.getInputStream(true);
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            speechService.recognizeUsingWebSocket(getRecognizeOptions(capture), new MicrophoneRecognizeDelegate());
          } catch (Exception e) {
            showError(e);
          }
        }
      }).start();
      listening = true;
      Toast.makeText(MainActivity.this, "Listening....Click to Stop", Toast.LENGTH_LONG).show();

    } else {
      try {
        microphoneHelper.closeInputStream();
        listening = false;
        Toast.makeText(MainActivity.this, "Stopped Listening....Click to Start", Toast.LENGTH_LONG).show();
      } catch (Exception e) {
        e.printStackTrace();
      }

    }
  }

  /**
   * Check Internet Connection
   *
   * @return
   */
  private boolean checkInternetConnection() {
    // get Connectivity Manager object to check connection
    ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean isConnected = activeNetwork != null &&
            activeNetwork.isConnectedOrConnecting();

    // Check for network connections
    if (isConnected) {
      return true;
    } else {
      Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
      return false;
    }

  }

  //Private Methods - Speech to Text
  private RecognizeOptions getRecognizeOptions(InputStream audio) {
    return new RecognizeOptions.Builder()
            .audio(audio)
            .contentType(ContentType.OPUS.toString())
            .model("en-US_BroadbandModel")
            .interimResults(true)
            .inactivityTimeout(2000)
            .build();
  }

  //Watson Speech to Text Methods.
  private class MicrophoneRecognizeDelegate extends BaseRecognizeCallback {
    @Override
    public void onTranscription(SpeechRecognitionResults speechResults) {
      if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
        String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
        showMicText(text);
      }
    }

    @Override
    public void onError(Exception e) {
      showError(e);
      enableMicButton();
    }

    @Override
    public void onDisconnected() {
      enableMicButton();
    }

  }

  private void showMicText(final String text) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        inputMessage.setText(text);
      }
    });
  }

  private void enableMicButton() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        btnRecord.setEnabled(true);
      }
    });
  }

  private void showError(final Exception e) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        e.printStackTrace();
      }
    });
  }


  /**
   * This is a very bare sample app to demonstrate the usage of the CameraDetector object from Affectiva.
   * It displays statistics on frames per second, percentage of time a face was detected, and the user's smile score.
   *
   * The app shows off the maneuverability of the SDK by allowing the user to start and stop the SDK and also hide the camera SurfaceView.
   *
   * For use with SDK 2.02
   */
  public class MyActivity extends Activity implements Detector.ImageListener, CameraDetector.CameraEventListener {

    final String LOG_TAG = "CameraDetectorDemo";

    Button startSDKButton;
    Button surfaceViewVisibilityButton;
    TextView angerTextView;
    TextView disgustTextView;
    TextView smileTextView;
    TextView fearTextView;
    ToggleButton toggleButton;

    SurfaceView cameraPreview;

    boolean isCameraBack = false;
    boolean isSDKStarted = false;

    RelativeLayout mainLayout;

    CameraDetector detector;

    int previewWidth = 0;
    int previewHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      smileTextView = (TextView) findViewById(R.id.smile_textview);
      fearTextView = (TextView) findViewById(R.id.fear_textview);
      angerTextView = (TextView) findViewById(R.id.anger_textview);
      disgustTextView = (TextView) findViewById(R.id.disgust_textview);

        /*

        toggleButton = (ToggleButton) findViewById(R.id.front_back_toggle_button);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isCameraBack = isChecked;
                switchCamera(isCameraBack? CameraDetector.CameraType.CAMERA_BACK : CameraDetector.CameraType.CAMERA_FRONT);
            }
        });

        */

        /*startSDKButton = (Button) findViewById(R.id.sdk_start_button);
        startSDKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSDKStarted) {
                    isSDKStarted = false;
                    stopDetector();
                    startSDKButton.setText("Start Camera");
                } else {
                    isSDKStarted = true;
                    startDetector();
                    startSDKButton.setText("Stop Camera");
                }
            }
        });
        */

      //We create a custom SurfaceView that resizes itself to match the aspect ratio of the incoming camera frames
      mainLayout = (RelativeLayout) findViewById(R.id.main_layout);
      cameraPreview = new SurfaceView(this) {
        @Override
        public void onMeasure(int widthSpec, int heightSpec) {
          int measureWidth = MeasureSpec.getSize(widthSpec);
          int measureHeight = MeasureSpec.getSize(heightSpec);
          int width;
          int height;
          if (previewHeight == 0 || previewWidth == 0) {
            width = measureWidth;
            height = measureHeight;
          } else {
            float viewAspectRatio = (float)measureWidth/measureHeight;
            float cameraPreviewAspectRatio = (float) previewWidth/previewHeight;

            if (cameraPreviewAspectRatio > viewAspectRatio) {
              width = measureWidth;
              height =(int) (measureWidth / cameraPreviewAspectRatio);
            } else {
              width = (int) (measureHeight * cameraPreviewAspectRatio);
              height = measureHeight;
            }
          }
          setMeasuredDimension(width,height);
        }
      };
      RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.addRule(RelativeLayout.CENTER_IN_PARENT,RelativeLayout.TRUE);
      cameraPreview.setLayoutParams(params);
      mainLayout.addView(cameraPreview,0);


      surfaceViewVisibilityButton = (Button) findViewById(R.id.surfaceview_visibility_button);
      surfaceViewVisibilityButton.setText("HIDE SURFACE VIEW");
      surfaceViewVisibilityButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (cameraPreview.getVisibility() == View.VISIBLE) {
            cameraPreview.setVisibility(View.INVISIBLE);
            surfaceViewVisibilityButton.setText("SHOW SURFACE VIEW");
          } else {
            cameraPreview.setVisibility(View.VISIBLE);
            surfaceViewVisibilityButton.setText("HIDE SURFACE VIEW");
          }
        }
      });

      detector = new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT, cameraPreview);
      detector.setDetectSmile(true);
      detector.setDetectAnger(true);
      detector.setDetectFear(true);
      detector.setDetectDisgust(true);
      detector.setImageListener(this);
      detector.setOnCameraEventListener(this);

      detector.start();
    }

    /*
    @Override

    protected void onResume() {
        super.onResume();
        if (isSDKStarted) {
            startDetector();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDetector();
    }

    void startDetector() {
        if (!detector.isRunning()) {
            detector.start();
        }
    }

    void stopDetector() {
        if (detector.isRunning()) {
            detector.stop();
        }
    }

    */

    /*

    void switchCamera(CameraDetector.CameraType type) {
        detector.setCameraType(type);
    } */

    @Override
    public void onImageResults(List<Face> list, Frame frame, float v) {
      if (list == null)
        return;
      if (list.size() == 0) {
        smileTextView.setText("NO FACE");
        angerTextView.setText("NO FACE");
        disgustTextView.setText("NO FACE");
        fearTextView.setText("NO FACE");

      } else {
        Face face = list.get(0);
        smileTextView.setText(String.format("SMILE\n%.2f",face.expressions.getSmile()));
        angerTextView.setText(String.format("ANGER\n%.2f",face.emotions.getAnger()));
        disgustTextView.setText(String.format("DISGUST\n%.2f",face.emotions.getDisgust()));
        fearTextView.setText(String.format("FEAR\n%.2f",face.emotions.getDisgust()));
        System.out.println("Working");


      }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public void onCameraSizeSelected(int width, int height, Frame.ROTATE rotate) {
      if (rotate == Frame.ROTATE.BY_90_CCW || rotate == Frame.ROTATE.BY_90_CW) {
        previewWidth = height;
        previewHeight = width;
      } else {
        previewHeight = height;
        previewWidth = width;
      }
      cameraPreview.requestLayout();
    }
  }


}







