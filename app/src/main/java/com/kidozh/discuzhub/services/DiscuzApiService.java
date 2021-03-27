package com.kidozh.discuzhub.services;

import com.kidozh.discuzhub.results.AddCheckResult;
import com.kidozh.discuzhub.results.DiscuzIndexResult;
import com.kidozh.discuzhub.results.BuyThreadResult;
import com.kidozh.discuzhub.results.DisplayThreadsResult;
import com.kidozh.discuzhub.results.FavoriteForumResult;
import com.kidozh.discuzhub.results.ApiMessageActionResult;
import com.kidozh.discuzhub.results.FavoriteThreadResult;
import com.kidozh.discuzhub.results.ForumResult;
import com.kidozh.discuzhub.results.HotForumsResult;
import com.kidozh.discuzhub.results.LoginResult;
import com.kidozh.discuzhub.results.NewThreadsResult;
import com.kidozh.discuzhub.results.PrivateMessageResult;
import com.kidozh.discuzhub.results.SecureInfoResult;
import com.kidozh.discuzhub.results.SmileyResult;
import com.kidozh.discuzhub.results.ThreadResult;
import com.kidozh.discuzhub.results.UserNoteListResult;

import java.util.HashMap;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface DiscuzApiService {
    public static String DISCUZ_API_PATH = "/api/mobile/index.php";

    @GET(DISCUZ_API_PATH+"?version=4&module=myfavthread")
    Call<FavoriteThreadResult> getFavoriteThreadResult(@Query("page") int page);

    @GET(DISCUZ_API_PATH+"?version=4&module=myfavforum")
    Call<FavoriteForumResult> getFavoriteForumResult(@Query("page") int page);

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=favthread&type=thread&ac=favorite&favoritesubmit=true")
    Call<ApiMessageActionResult> favoriteThreadActionResult(
            @Query("formhash") String formhash,
            @Query("id") int tid,
            @Field("description") String description


    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=favthread&type=all&ac=favorite&op=delete&inajax=1&favoritesubmit=true")
    Call<ApiMessageActionResult> unfavoriteThreadActionResult(
            @Field("formhash") String formhash,
            @Field("deletesubmit") String submit,
            @Field("handlekey") String handleKey,
            @Query("favid") int favid


    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=favforum&type=forum&ac=favorite&favoritesubmit=true")
    Call<ApiMessageActionResult> favoriteForumActionResult(
            @Query("formhash") String formhash,
            @Query("id") int tid,
            @Field("description") String description
    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=favforum&type=all&ac=favorite&op=delete&inajax=1&favoritesubmit=true")
    Call<ApiMessageActionResult> unfavoriteForumActionResult(
            @Field("formhash") String formhash,
            @Field("deletesubmit") String submit,
            @Field("handlekey") String handleKey,
            @Query("favid") int favid
    );


    @GET(DISCUZ_API_PATH+"?version=4&module=mobilesign")
    Call<ApiMessageActionResult> mobileSignActionResult(
            @Query("hash") String formHash
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=mynotelist")
    Call<UserNoteListResult> userNotificationListResult(
            @Query("page") int page
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=login&mod=logging&action=login")
    Call<LoginResult> getLoginResult();

    @GET(DISCUZ_API_PATH+"?version=4&module=hotforum")
    Call<HotForumsResult> hotForumResult();

    @GET(DISCUZ_API_PATH+"?version=4&module=hotthread")
    Call<DisplayThreadsResult> hotThreadResult(
            @Query("page") int page
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=forumindex")
    Call<DiscuzIndexResult> indexResult();

    @GET(DISCUZ_API_PATH+"?version=4&module=forumdisplay")
    Call<ForumResult> forumDisplayResult(@QueryMap HashMap<String,String> options);

    @GET(DISCUZ_API_PATH+"?version=4&module=viewthread")
    Call<ThreadResult> viewThreadResult(@QueryMap HashMap<String,String> options);

    @GET(DISCUZ_API_PATH+"?version=4&module=secure")
    Call<SecureInfoResult> secureResult(@Query("type") String type);

    @GET(DISCUZ_API_PATH+"?version=4&module=secure")
    Call<SecureInfoResult> captchaCall(@Query("type") String type);

    @GET(DISCUZ_API_PATH+"?version=4&module=recommend")
    Call<ApiMessageActionResult> recommendThread(
            @Query("hash") String formhash,
            @Query("tid") int tid
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=recommend&do=substract")
    Call<ApiMessageActionResult> unrecommendThread(
            @Query("hash") String formhash,
            @Query("tid") int tid
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=buythread")
    Call<BuyThreadResult> getThreadPriceInfo(
            @Query("tid") int tid
    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=buythread&paysubmit=yes")
    Call<BuyThreadResult> buyThread(
            @Field("tid") int tid,
            @Field("formhash") String formhash,
            @Query("handlekey") String pay
    );

    @POST(DISCUZ_API_PATH+"?version=5&module=report&reportsubmit=true&rtype=post&inajax=1")
    Call<ApiMessageActionResult> reportPost(
        @Query("formhash") String formhash,
        @Query("rid") int pid,
        @Query("report_select") String option,
        @Query("message") String message
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=check")
    Call<AddCheckResult> getCheckResult();

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=login")
    Call<LoginResult> loginCall(@FieldMap HashMap<String,String> options);

    @GET(DISCUZ_API_PATH+"?version=4&module=newthreads&limit=20")
    Call<NewThreadsResult> newThreadsResult(
            @Query("fids") String fids,
            @Query("start") int start
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=smiley")
    Call<SmileyResult> getSmileyResult();

    @GET(DISCUZ_API_PATH+"?version=4&module=mypm&subop=view")
    Call<PrivateMessageResult> getPrivateMessageListResult(
            @Query("touid") int toUid,
            @Query("page") String pageString
    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&ac=pm&op=send&daterange=0&module=sendpm&pmsubmit=yes")
    Call<ApiMessageActionResult> sendPrivateMessage(
            @Query("plid") int plid,
            @Query("pmid") int pmid,
            @Field("formhash") String formHash,
            @Field("message") String message,
            @Field("touid") String toUid
    );

    @GET(DISCUZ_API_PATH+"?version=4&module=mythread")
    Call<DisplayThreadsResult> myThreadResult(
            @Query("page") int page
    );

    @FormUrlEncoded
    @POST(DISCUZ_API_PATH+"?version=4&module=sendreply&action=reply&replysubmit=yes")
    Call<ApiMessageActionResult> replyThread(
            @Query("fid") int fid,
            @Query("tid") int tid,
            @FieldMap HashMap<String,String> options
    );


}
