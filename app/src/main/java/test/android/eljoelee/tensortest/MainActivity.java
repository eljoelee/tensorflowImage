package test.android.eljoelee.tensortest;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.yongchun.library.view.ImageSelectorActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private NetworkService networkService;
    private TextView content;

    private static final int INPUT_SIZE = 299; //인식시킬 이미지 사이즈
    private static final int IMAGE_MEAN = 0;
    private static final float IMAGE_STD = 255.0f;

    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";

    private static final String MODEL_FILE = "file:///android_asset/optimized_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/retrained_labels.txt";

    private Classifier classifier;
    private Executor executor = Executors.newSingleThreadExecutor(); //그래프 파일이 크기 때문에 로딩을 쓰레드로 돌리기 위해 사용

    private TextView txtResult;
    private ImageView imgResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtResult = (TextView)findViewById(R.id.txtResult);
        imgResult = (ImageView)findViewById(R.id.imgResult);

        Button btnGallery = (Button) findViewById(R.id.btnGallery);
        btnGallery.setOnClickListener(this);
        content = (TextView) findViewById(R.id.textContent);

        ApplicationController applicationController = ApplicationController.getInstance();
        applicationController.buildNetworkService("http://6a7de396.ngrok.io", 8000);

        networkService = ApplicationController.getInstance().getNetworkService();

        //텐서플로우 초기화 및 그래프파일 메모리에 탑재
        initTensorFlowAndLoadModel();

        //각종 권한 체크를 쉽게 도와주는 Dexter 라이브러리
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                ).withListener(new MultiplePermissionsListener() {
            @Override public void onPermissionsChecked(MultiplePermissionsReport report) {/* ... */}
            @Override public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
        }).check();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        switch(id) {
            case R.id.btnGallery:
                LoadImageFromGallery();
                break;
            default:
                break;
        }
    }

    private void LoadImageFromGallery() {
        // 이미지를 한장만 선택하도록 이미지피커 실행
        ImageSelectorActivity.start(MainActivity.this, 1, ImageSelectorActivity.MODE_SINGLE, false,false,false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // 이미지피커에서 선택된 이미지를 텐서플로우로 넘김.
        // 이미지피커는 ArrayList 로 값을 리턴합니다.
        if(resultCode == RESULT_OK && requestCode == ImageSelectorActivity.REQUEST_IMAGE){
            ArrayList<String> images = (ArrayList<String>) data.getSerializableExtra(ImageSelectorActivity.REQUEST_OUTPUT);

            // 이미지는 안드로이드용 텐서플로우가 인식할 수 있는 포맷인 비트맵으로 변환해서 텐서플로우에 넘깁니다
            Bitmap bitmap = BitmapFactory.decodeFile(images.get(0));

            recognize_bitmap(bitmap);
        }
    }

    private void recognize_bitmap(Bitmap bitmap) {

        // 비트맵을 상수로 정의된 INPUT SIZE에 맞춰 조절
        bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

        // classifier 의 recognizeImage 부분이 실제 inference 를 호출해서 인식작업을 하는 부분
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        //Classifier.Recognition 구조로 리턴되는 결과값을 추출함.
        //results List에 들어있는 값들을 하나씩 'val' 변수에 대입
        for(Classifier.Recognition val : results) {
            content.setText(null);
            int per = (int) Math.floor(val.getConfidence()*100);
            if (per >= 70) {
                txtResult.setText(val.getTitle());
                //Django REST Framework 연동부분
                Call<List<Dogs>> dogsCall = networkService.get_name_dogs(val.getTitle());
                dogsCall.enqueue(new Callback<List<Dogs>>() {
                    @Override
                    public void onResponse(Call<List<Dogs>> call, Response<List<Dogs>> response) {
                        if (response.isSuccessful()) {
                            List<Dogs> dogsList = response.body();
                            for (Dogs dog : dogsList) {
                                content.setText(
                                        dog.getDogName()
                                                + "\r\n\r\n"
                                                + "정보" + "\r\n"
                                                + dog.getDogInfo()
                                                + "\r\n\r\n"
                                                + "출생지" + "\r\n"
                                                + dog.getDogPob()
                                                + "\r\n\r\n"
                                                + "성격" + "\r\n"
                                                + dog.getDogPersonality()
                                );
                            }
                        } else {
                            int statusCode = response.code();
                            Log.i("My Tag", "응답코드 : " + statusCode);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Dogs>> call, Throwable t) {
                        Log.i("My Tag", "서버 onFailure 에러내용 : " + t.getMessage());
                    }
                });
            }else{
                txtResult.setText("댕댕이를 인식 못했어요! 정확한 사진을 올려주세요.");
            }
        }
        // imgResult에는 분석에 사용된 비트맵을 뿌려줍니다.
        imgResult.setImageBitmap(bitmap);
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_FILE,
                            LABEL_FILE,
                            INPUT_SIZE,
                            IMAGE_MEAN,
                            IMAGE_STD,
                            INPUT_NAME,
                            OUTPUT_NAME);
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

}
