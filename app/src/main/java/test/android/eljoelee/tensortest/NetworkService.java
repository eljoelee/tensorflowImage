package test.android.eljoelee.tensortest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by eljoe on 2017-07-19.
 */

public interface NetworkService {
    @GET("/dogs/")
    Call<List<Dogs>> get_name_dogs(@Query("dogEngName") String dogName);
}