package com.kidozh.discuzhub.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.kidozh.discuzhub.R;
import com.kidozh.discuzhub.dialogs.ForumDisplayOptionFragment;
import com.kidozh.discuzhub.adapter.NetworkIndicatorAdapter;
import com.kidozh.discuzhub.adapter.SubForumAdapter;
import com.kidozh.discuzhub.adapter.ThreadAdapter;
import com.kidozh.discuzhub.daos.FavoriteForumDao;
import com.kidozh.discuzhub.daos.ViewHistoryDao;
import com.kidozh.discuzhub.database.FavoriteForumDatabase;
import com.kidozh.discuzhub.database.ViewHistoryDatabase;
import com.kidozh.discuzhub.databinding.ActivityBbsShowForumBinding;
import com.kidozh.discuzhub.entities.Discuz;
import com.kidozh.discuzhub.entities.DisplayForumQueryStatus;
import com.kidozh.discuzhub.entities.FavoriteForum;
import com.kidozh.discuzhub.entities.Thread;
import com.kidozh.discuzhub.entities.User;
import com.kidozh.discuzhub.entities.ViewHistory;
import com.kidozh.discuzhub.entities.Forum;
import com.kidozh.discuzhub.results.ApiMessageActionResult;
import com.kidozh.discuzhub.results.ForumResult;
import com.kidozh.discuzhub.results.MessageResult;
import com.kidozh.discuzhub.services.DiscuzApiService;
import com.kidozh.discuzhub.utilities.AnimationUtils;
import com.kidozh.discuzhub.utilities.GlideImageGetter;
import com.kidozh.discuzhub.utilities.UserPreferenceUtils;
import com.kidozh.discuzhub.utilities.VibrateUtils;
import com.kidozh.discuzhub.utilities.ConstUtils;
import com.kidozh.discuzhub.utilities.bbsLinkMovementMethod;
import com.kidozh.discuzhub.utilities.URLUtils;
import com.kidozh.discuzhub.utilities.NetworkUtils;
import com.kidozh.discuzhub.utilities.numberFormatUtils;
import com.kidozh.discuzhub.viewModels.ForumViewModel;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


import es.dmoral.toasty.Toasty;
import retrofit2.Retrofit;

public class ForumActivity extends BaseStatusActivity implements
        NetworkIndicatorAdapter.OnRefreshBtnListener,
        bbsLinkMovementMethod.OnLinkClickedListener{
    private static final String TAG = ForumActivity.class.getSimpleName();

    private Forum forum;


    private ThreadAdapter adapter;
    private SubForumAdapter subForumAdapter;
    private User user;

    String fid;

    //MutableLiveData<bbsDisplayForumQueryStatus> forumStatusMutableLiveData;

    ForumViewModel forumViewModel;
    private boolean hasLoadOnce = false;
    
    ActivityBbsShowForumBinding binding;
    NetworkIndicatorAdapter networkIndicatorAdapter = new NetworkIndicatorAdapter();
    ConcatAdapter concatAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBbsShowForumBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        forumViewModel = new ViewModelProvider(this).get(ForumViewModel.class);
        configureIntentData();
        bindViewModel();

        Log.d(TAG, "Get bbs information "+bbsInfo);

        initLiveData();
        configureActionBar();

        configureFab();
        configureForumInfo();
        configureRecyclerview();
        configureSwipeRefreshLayout();
        setForumRuleCollapseListener();
        configurePostThreadBtn();
        //  start to get the first page info
        forumViewModel.getNextThreadList();


    }



    private void configureIntentData(){
        Intent intent = getIntent();
        forum = (Forum) intent.getSerializableExtra(ConstUtils.PASS_FORUM_THREAD_KEY);
        bbsInfo = (Discuz) intent.getSerializableExtra(ConstUtils.PASS_BBS_ENTITY_KEY);
        user = (User) intent.getSerializableExtra(ConstUtils.PASS_BBS_USER_KEY);
        URLUtils.setBBS(bbsInfo);
        fid = String.valueOf(forum.fid);
        forumViewModel.setBBSInfo(bbsInfo, user,forum);
        // hasLoadOnce = intent.getBooleanExtra(bbsConstUtils.PASS_IS_VIEW_HISTORY,false);
    }


    private void bindViewModel(){
        forumViewModel.totalThreadListMutableLiveData.observe(this, it->{
            Map<String,String> threadTypeMap = null;
            if(forumViewModel.displayForumResultMutableLiveData.getValue()!=null &&
                    forumViewModel.displayForumResultMutableLiveData.getValue().forumVariables.getThreadTypeInfo()!=null){
                threadTypeMap = forumViewModel.displayForumResultMutableLiveData.getValue().forumVariables.getThreadTypeInfo().idNameMap;
            }

            adapter.updateListAndType(it, threadTypeMap);
            if(forumViewModel.forumStatusMutableLiveData.getValue()!= null){
                int page = forumViewModel.forumStatusMutableLiveData.getValue().page;
                // point to the next page
                if(page == 2){
                    binding.bbsForumThreadRecyclerview.smoothScrollToPosition(0);
                }
            }

        });



        forumViewModel.networkState.observe(this, integer -> {
            Log.d(TAG,"Network state changed "+integer);
            switch (integer){
                case ConstUtils.NETWORK_STATUS_LOADING:{
                    binding.bbsForumInfoSwipeRefreshLayout.setRefreshing(true);
                    networkIndicatorAdapter.setLoadingStatus();
                    break;
                }
                case ConstUtils.NETWORK_STATUS_LOADED_ALL:{
                    binding.bbsForumInfoSwipeRefreshLayout.setRefreshing(false);
                    //Log.d(TAG,"Network changed "+integer);
                    networkIndicatorAdapter.setLoadedAllStatus();
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
                    boolean needVibrate = prefs.getBoolean(getString(R.string.preference_key_vibrate_when_load_all),true);
                    Toasty.success(getApplication(),getString(R.string.thread_has_load_all),Toast.LENGTH_SHORT).show();
                    if(needVibrate){
                        VibrateUtils.vibrateSlightly(this);

                    }
                    break;
                }
                case ConstUtils.NETWORK_STATUS_SUCCESSFULLY:{
                    binding.bbsForumInfoSwipeRefreshLayout.setRefreshing(false);
                    networkIndicatorAdapter.setLoadSuccessfulStatus();

                    break;
                }
                default:{
                    binding.bbsForumInfoSwipeRefreshLayout.setRefreshing(false);
                }
            }
        });
        
        forumViewModel.errorMessageMutableLiveData.observe(this,errorMessage -> {
            Log.d(TAG,"recv error message "+errorMessage);
            if(errorMessage!=null){
                Toasty.error(getApplicationContext(),
                        getString(R.string.discuz_api_message_template,errorMessage.key,errorMessage.content),
                        Toast.LENGTH_LONG).show();
                networkIndicatorAdapter.setErrorStatus(errorMessage);
                VibrateUtils.vibrateForError(getApplication());
            }
        });

        
        forumViewModel.displayForumResultMutableLiveData.observe(this, new Observer<ForumResult>() {
            @Override
            public void onChanged(ForumResult forumResult) {
                // deal with sublist
                Log.d(TAG,"GET result "+forumResult);
                if(forumResult !=null && forumResult.forumVariables!=null){
                    Log.d(TAG,"GET sublist size "+forumResult.forumVariables.subForumLists.size());
                    subForumAdapter.setSubForumInfoList(forumResult.forumVariables.subForumLists);
                    if(forumResult.forumVariables.forum !=null){
                        
                        Forum forum = forumResult.forumVariables.forum;
                        ForumActivity.this.forum = forum;
                        binding.toolbarTitle.setText(forum.name);
                        if(forum.description.isEmpty()){
                            binding.toolbarSubtitle.setVisibility(View.GONE);
                        }
                        else{
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            binding.toolbarSubtitle.setText(forum.description);
                        }


                    }
                }
            }

        });


        forumViewModel.favoriteForumLiveData.observe(this, favoriteForum -> {
            Log.d(TAG,"Detecting change favorite forum "+favoriteForum);
            if(favoriteForum!=null){
                Log.d(TAG,"favorite forum id "+favoriteForum.id);
            }
            invalidateOptionsMenu();
        });

        forumViewModel.displayForumResultMutableLiveData.observe(this, result ->{
            if(result!=null){
                Forum forum = result.forumVariables.forum;
                if(!hasLoadOnce){
                    recordViewHistory(forum);
                    hasLoadOnce = true;
                }

                if(!binding.bbsForumRuleTextview.getText().equals(forum.rules)){
                    String s = forum.rules;
                    if(s!=null && s.length() !=0){
                        GlideImageGetter glideImageGetter  = new GlideImageGetter(binding.bbsForumRuleTextview, user);
                        GlideImageGetter.HtmlTagHandler htmlTagHandler = new GlideImageGetter.HtmlTagHandler(getApplicationContext(),binding.bbsForumRuleTextview);
                        Spanned sp = Html.fromHtml(s,glideImageGetter,htmlTagHandler);
                        SpannableString spannableString = new SpannableString(sp);
                        // binding.bbsForumAlertTextview.setAutoLinkMask(Linkify.ALL);
                        binding.bbsForumRuleTextview.setMovementMethod(new bbsLinkMovementMethod(ForumActivity.this));
                        binding.bbsForumRuleTextview.setText(spannableString, TextView.BufferType.SPANNABLE);
                        //collapseTextView(binding.bbsForumRuleTextview,3);
                    }
                    else {
                        binding.bbsForumRuleTextview.setText(R.string.bbs_rule_not_set);
                        binding.bbsForumRuleTextview.setVisibility(View.GONE);
                    }
                }


                // for description
                if(!binding.bbsForumAlertTextview.getText().equals(forum.description)){
                    String s = forum.description;
                    if(s!=null && s.length() !=0){
                        GlideImageGetter glideImageGetter  = new GlideImageGetter(binding.bbsForumAlertTextview, user);
                        GlideImageGetter.HtmlTagHandler htmlTagHandler = new GlideImageGetter.HtmlTagHandler(getApplicationContext(),binding.bbsForumRuleTextview);
                        Spanned sp = Html.fromHtml(s,glideImageGetter,htmlTagHandler);
                        SpannableString spannableString = new SpannableString(sp);
                        // binding.bbsForumAlertTextview.setAutoLinkMask(Linkify.ALL);
                        binding.bbsForumAlertTextview.setMovementMethod(new bbsLinkMovementMethod(ForumActivity.this));
                        binding.bbsForumAlertTextview.setText(spannableString, TextView.BufferType.SPANNABLE);
                    }
                    else {
                        binding.bbsForumAlertTextview.setText(R.string.bbs_forum_description_not_set);
                        binding.bbsForumAlertTextview.setVisibility(View.GONE);
                    }
                }

            }
        });


        forumViewModel.ruleTextCollapse.observe(this,aBoolean -> {

            if(aBoolean){
                Log.d(TAG,"Collapse rule text "+aBoolean);
                binding.bbsForumRuleTextview.setMaxLines(5);
            }
            else {

                binding.bbsForumRuleTextview.setMaxLines(Integer.MAX_VALUE);
            }
        });

    }

    private void setForumRuleCollapseListener(){
        binding.bbsForumRuleTextview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forumViewModel.toggleRuleCollapseStatus();
            }
        });
    }

    private void initLiveData(){

        DisplayForumQueryStatus forumStatus = new DisplayForumQueryStatus(forum.fid,1);
        forumViewModel.forumStatusMutableLiveData.setValue(forumStatus);

    }

    ForumDisplayOptionFragment forumDisplayOptionFragment = new ForumDisplayOptionFragment();


    private void recordViewHistory(Forum forum){
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean recordHistory = prefs.getBoolean(getString(R.string.preference_key_record_history),false);
        if(recordHistory){

            new InsertViewHistory(new ViewHistory(
                    forum.iconUrl,
                    forum.name,
                    bbsInfo.getId(),
                    forum.description,
                    ViewHistory.VIEW_TYPE_FORUM,
                    forum.fid,
                    0,
                    new Date()
            )).execute();
        }
    }

    private void reConfigureAndRefreshPage(DisplayForumQueryStatus status){
        status.hasLoadAll = false;
        status.page = 1;
        forumViewModel.forumStatusMutableLiveData.postValue(status);
        forumViewModel.setForumStatusAndFetchThread(forumViewModel.forumStatusMutableLiveData.getValue());

    }

    private void configureSwipeRefreshLayout(){
        binding.bbsForumInfoSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                DisplayForumQueryStatus status = forumViewModel.forumStatusMutableLiveData.getValue();
                reConfigureAndRefreshPage(status);

            }
        });
    }

    private void configurePostThreadBtn(){
        Context context = this;
        if(user == null){
            binding.bbsForumFab.setVisibility(View.GONE);
        }

        binding.bbsForumFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(forumViewModel.displayForumResultMutableLiveData.getValue() != null
                        && forumViewModel.displayForumResultMutableLiveData.getValue().forumVariables != null){
                    User User = forumViewModel.displayForumResultMutableLiveData.getValue()
                            .forumVariables.getUserBriefInfo();
                    if(User !=null && User.isValid()){
                        Intent intent = new Intent(context, PublishActivity.class);
                        intent.putExtra("fid",fid);
                        intent.putExtra("fid_name",forum.name);
                        intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
                        intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
                        intent.putExtra(ConstUtils.PASS_POST_TYPE, ConstUtils.TYPE_POST_THREAD);
                        if(forumViewModel.displayForumResultMutableLiveData
                                .getValue().forumVariables.getThreadTypeInfo() != null){
                            intent.putExtra(ConstUtils.PASS_THREAD_CATEGORY_KEY, (Serializable) forumViewModel.displayForumResultMutableLiveData
                                    .getValue().forumVariables.getThreadTypeInfo().idNameMap);
                        }


                        Log.d(TAG,"You pass fid name"+forum.name);

                        startActivity(intent);
                    }
                    else {
                        Toasty.info(context,context.getString(R.string.bbs_require_login_to_comment), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(context, LoginActivity.class);
                        intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
                        intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
                        startActivity(intent);
                    }
                }
                else {
                    binding.bbsForumFab.setVisibility(View.GONE);
                }



            }
        });
    }



    private void configureActionBar(){
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        binding.toolbarTitle.setText(bbsInfo.site_name);
        if(forum.name !=null){
            binding.toolbarTitle.setText(forum.name);
            //getSupportActionBar().setSubtitle(forum.name);
        }
    }





    private void configureForumInfo(){

        binding.bbsForumThreadNumberTextview.setText(numberFormatUtils.getShortNumberText(forum.threads));
        binding.bbsForumPostNumberTextview.setText(numberFormatUtils.getShortNumberText(forum.posts));
    }

    private void configureRecyclerview(){
        binding.bbsForumSublist.setHasFixedSize(true);
        binding.bbsForumSublist.setItemAnimator(AnimationUtils.INSTANCE.getRecyclerviewAnimation(this));
        binding.bbsForumSublist.setLayoutManager(new GridLayoutManager(this,4));
        subForumAdapter = new SubForumAdapter(bbsInfo, user);
        binding.bbsForumSublist.setAdapter(AnimationUtils.INSTANCE.getAnimatedAdapter(this,subForumAdapter));

        binding.bbsForumThreadRecyclerview.setHasFixedSize(true);
        binding.bbsForumThreadRecyclerview.setItemAnimator(AnimationUtils.INSTANCE.getRecyclerviewAnimation(this));
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);

        binding.bbsForumThreadRecyclerview.setLayoutManager(linearLayoutManager);

        adapter = new ThreadAdapter(null,bbsInfo, user);
        concatAdapter = new ConcatAdapter(adapter,networkIndicatorAdapter);
        binding.bbsForumThreadRecyclerview.setAdapter(AnimationUtils.INSTANCE.getAnimatedAdapter(this,concatAdapter));

        binding.bbsForumThreadRecyclerview.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE){
                    boolean hasLoadAll = forumViewModel.networkState.getValue() == ConstUtils.NETWORK_STATUS_LOADED_ALL;
                    boolean loading = forumViewModel.networkState.getValue() == ConstUtils.NETWORK_STATUS_LOADING;
                    boolean loadAllOnce = forumViewModel.loadAllNoticeOnce.getValue();
                    Log.d(TAG,"load all "+hasLoadAll+" page "+ forumViewModel.forumStatusMutableLiveData.getValue().page);
                    if(hasLoadAll){
                        if(!loadAllOnce){
                            Toasty.success(getApplication()
                                    ,getString(R.string.has_load_all_threads_in_forum,adapter.getItemCount()),Toast.LENGTH_LONG).show();
                            VibrateUtils.vibrateSlightly(getApplication());
                            forumViewModel.loadAllNoticeOnce.postValue(true);
                        }

                    }
                    else {
                        if(!loading){
                            DisplayForumQueryStatus status = forumViewModel.forumStatusMutableLiveData.getValue();
                            if(status!=null){
                                forumViewModel.setForumStatusAndFetchThread(status);
                            }
                        }
                    }


                }
            }
        });

    }

    private void configureFab(){

    }

    @Override
    public boolean onLinkClicked(String url) {
        return bbsLinkMovementMethod.parseURLAndOpen(this,bbsInfo, user,url);
    }

    @Override
    public void onRefreshBtnClicked() {
        DisplayForumQueryStatus status = forumViewModel.forumStatusMutableLiveData.getValue();
        if(status!=null){
            forumViewModel.setForumStatusAndFetchThread(status);
        }
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        String currentUrl = "";
        DisplayForumQueryStatus forumStatus = forumViewModel.forumStatusMutableLiveData.getValue();
        if(forumStatus == null || forumStatus.page == 1){
            currentUrl = URLUtils.getForumDisplayUrl(fid,"1");
        }
        else {
            currentUrl = URLUtils.getForumDisplayUrl(fid,String.valueOf(forumStatus.page-1));
        }
        int id = item.getItemId();
        if(id == android.R.id.home){
            this.finishAfterTransition();
            return false;
        }
        else if(id == R.id.forum_filter){
            forumDisplayOptionFragment.show(getSupportFragmentManager(),ForumDisplayOptionFragment.class.getSimpleName());
            return true;
        }
        else if(id == R.id.bbs_forum_nav_personal_center){
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
            intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
            intent.putExtra("UID",String.valueOf(user.uid));
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_forum_nav_draft_box){
            Intent intent = new Intent(this, ThreadDraftActivity.class);
            intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
            intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
            startActivity(intent,null);
            return true;
        }
        else if(id == R.id.bbs_forum_nav_show_in_webview){
            Intent intent = new Intent(this, InternalWebViewActivity.class);
            intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
            intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
            intent.putExtra(ConstUtils.PASS_URL_KEY,currentUrl);
            Log.d(TAG,"Inputted URL "+currentUrl);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_search){
            Intent intent = new Intent(this, SearchPostsActivity.class);
            intent.putExtra(ConstUtils.PASS_BBS_ENTITY_KEY,bbsInfo);
            intent.putExtra(ConstUtils.PASS_BBS_USER_KEY, user);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_forum_nav_show_in_external_browser){
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl));
            Log.d(TAG,"Inputted URL "+currentUrl);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_settings){
            Intent intent = new Intent(this,SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_about_app){
            Intent intent = new Intent(this, AboutAppActivity.class);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.bbs_share){
            ForumResult result = forumViewModel.displayForumResultMutableLiveData.getValue();
            if(result!=null && result.forumVariables!=null && result.forumVariables.forum !=null){
                Forum forum = result.forumVariables.forum;
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_template,
                        forum.name,URLUtils.getForumDisplayUrl(String.valueOf(this.forum.fid),"1")));
                sendIntent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }
            else {
                Toasty.info(this,getString(R.string.share_not_prepared),Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        else if(id == R.id.bbs_favorite){
            ForumResult result = forumViewModel.displayForumResultMutableLiveData.getValue();
            if(result!=null && result.forumVariables!=null && result.forumVariables.forum !=null){
                Forum forum = result.forumVariables.forum;

                FavoriteForum favoriteForum = forum.toFavoriteForm(bbsInfo.getId(),
                        user !=null? user.getUid():0
                );
                // save it to the database
                // boolean isFavorite = threadDetailViewModel.isFavoriteThreadMutableLiveData.getValue();
                FavoriteForum favoriteForumInDB = forumViewModel.favoriteForumLiveData.getValue();
                Log.d(TAG,"Get db favorite formD "+favoriteForumInDB);
                boolean isFavorite = favoriteForumInDB != null;
                if(isFavorite){

                    new FavoritingForumAsyncTask(favoriteForumInDB,false).execute();

                }
                else {
                    // open up a dialog
                    launchFavoriteForumDialog(favoriteForum);
                    //new FavoritingThreadAsyncTask(favoriteThread,true).execute();
                }

            }
            else {
                Toasty.info(this,getString(R.string.favorite_thread_not_prepared),Toast.LENGTH_SHORT).show();
            }

            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }



    }

    public class InsertViewHistory extends AsyncTask<Void,Void,Void>{

        ViewHistory viewHistory;
        ViewHistoryDao dao;

        public InsertViewHistory(ViewHistory viewHistory){
            this.viewHistory = viewHistory;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            dao = ViewHistoryDatabase
                    .getInstance(getApplicationContext())
                    .getDao();
            List<ViewHistory> viewHistories = dao
                    .getViewHistoryByBBSIdAndFid(viewHistory.belongedBBSId,viewHistory.fid);
            if(viewHistories ==null || viewHistories.size() == 0){
                dao.insert(viewHistory);
            }
            else {

                for(int i=0 ;i<viewHistories.size();i++){
                    ViewHistory updatedViewHistory = viewHistories.get(i);
                    updatedViewHistory.recordAt = new Date();
                }
                dao.insert(viewHistories);
            }

            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // configureIntentData();
        if(user == null){
            getMenuInflater().inflate(R.menu.menu_incognitive_forum_nav_menu, menu);
        }
        else {
            getMenuInflater().inflate(R.menu.bbs_forum_nav_menu,menu);

        }




        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        FavoriteForum favoriteForum = forumViewModel.favoriteForumLiveData.getValue();
        boolean isFavorite = favoriteForum != null;
        Log.d(TAG,"Triggering favorite status "+isFavorite+" "+favoriteForum);
        if(!isFavorite){
            menu.findItem(R.id.bbs_favorite).setIcon(ContextCompat.getDrawable(this,R.drawable.ic_not_favorite_24px));
            menu.findItem(R.id.bbs_favorite).setTitle(R.string.favorite);
        }
        else {
            menu.findItem(R.id.bbs_favorite).setIcon(ContextCompat.getDrawable(this,R.drawable.ic_favorite_24px));
            menu.findItem(R.id.bbs_favorite).setTitle(R.string.unfavorite);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void launchFavoriteForumDialog(FavoriteForum favoriteForum){
        AlertDialog.Builder favoriteDialog = new AlertDialog.Builder(this);
        favoriteDialog.setTitle(R.string.favorite_description);
        final EditText input = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        input.setLayoutParams(lp);

        favoriteDialog.setView(input);

        favoriteDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String description = input.getText().toString();
                description = TextUtils.isEmpty(description) ?"" :description;
                new FavoritingForumAsyncTask(favoriteForum,true,description).execute();

            }
        });

        favoriteDialog.show();


    }

    public class FavoritingForumAsyncTask extends AsyncTask<Void,Void,Boolean> {

        FavoriteForum favoriteForum;
        FavoriteForumDao dao;
        boolean favorite, error=false;
        Retrofit retrofit;
        retrofit2.Call<ApiMessageActionResult> favoriteForumActionResultCall;
        MessageResult messageResult;
        String description = "";

        public FavoritingForumAsyncTask(FavoriteForum favoriteForum, boolean favorite){

            this.favoriteForum = favoriteForum;
            this.favorite = favorite;
        }

        public FavoritingForumAsyncTask(FavoriteForum favoriteForum, boolean favorite, String description){

            this.favoriteForum = favoriteForum;
            this.favorite = favorite;
            this.description = description;
            favoriteForum.description = description;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            retrofit = NetworkUtils.getRetrofitInstance(bbsInfo.base_url,forumViewModel.client);
            DiscuzApiService service = retrofit.create(DiscuzApiService.class);
            ForumResult result = forumViewModel.displayForumResultMutableLiveData.getValue();
            dao = FavoriteForumDatabase.getInstance(getApplicationContext()).getDao();
            if(result !=null
                    && result.forumVariables!=null
                    && favoriteForum.userId !=0
                    && UserPreferenceUtils.syncInformation(getApplication())){
                Log.d(TAG,"Favorite formhash "+ result.forumVariables.formHash);
                if(favorite){

                    favoriteForumActionResultCall = service.favoriteForumActionResult(
                            result.forumVariables.formHash
                            , favoriteForum.idKey,description);
                }
                else {
                    Log.d(TAG,"Favorite id "+ favoriteForum.favid);

                    if(favoriteForum.favid == 0){
                        // just remove it from database
                    }
                    else {
                        favoriteForumActionResultCall = service.unfavoriteForumActionResult(
                                result.forumVariables.formHash,
                                "true",
                                "a_delete_"+ favoriteForum.favid,
                                favoriteForum.favid);
                    }

                }

            }


        }

        @Override
        protected Boolean doInBackground(Void... voids) {



            if(favoriteForumActionResultCall!=null){
                try {
                    Log.d(TAG,"request favorite url "+favoriteForumActionResultCall.request().url());
                    retrofit2.Response<ApiMessageActionResult> response = favoriteForumActionResultCall.execute();
                    //Log.d(TAG,"get response "+response.raw().body().string());
                    if(response.isSuccessful() && response.body() !=null){

                        ApiMessageActionResult result = response.body();

                        messageResult = result.message;
                        String key = result.message.key;
                        if(favorite && key.equals("favorite_do_success")){
                            dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                            dao.insert(favoriteForum);
                        }
                        if(favorite && key.equals("favorite_repeat")){
                            dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                            dao.insert(favoriteForum);
                        }
                        else if(!favorite && key.equals("do_success")){
                            if(favoriteForum !=null){
                                dao.delete(favoriteForum);
                            }
                            dao.delete(bbsInfo.getId(), user.getUid(), favoriteForum.idKey);
                        }
                        else {
                            error = true;

                        }

                    }
                    else {
                        messageResult = new MessageResult();
                        messageResult.content = getString(R.string.network_failed);
                        messageResult.key = String.valueOf(response.code());
                        if(favorite){
                            dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                            dao.insert(favoriteForum);

                            return true;
                        }
                        else {

                            // clear potential
                            dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                            //dao.delete(favoriteThread);
                            Log.d(TAG,"Just remove it from database "+favoriteForum.idKey);
                            return false;

                        }
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                    error = true;
                    messageResult = new MessageResult();
                    messageResult.content = e.getMessage();
                    messageResult.key = e.toString();
                    // insert as local database
                    if(favorite){
                        dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                        dao.insert(favoriteForum);

                        return true;
                    }
                    else {
                        // clear potential
                        dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                        //dao.delete(favoriteThread);
                        Log.d(TAG,"Just remove it from database "+favoriteForum.idKey);
                        return false;

                    }
                }

            }
            else {
                if(favorite){
                    dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                    dao.insert(favoriteForum);

                    return true;
                }
                else {
                    // clear potential
                    dao.delete(bbsInfo.getId(), user !=null? user.getUid():0, favoriteForum.idKey);
                    //dao.delete(favoriteThread);
                    Log.d(TAG,"Just remove it from database "+favoriteForum.idKey);
                    return false;

                }
            }
            return favorite;

        }


        @Override
        protected void onPostExecute(Boolean favorite) {
            super.onPostExecute(favorite);
            if(messageResult!=null){
                String key = messageResult.key;
                if(favorite && key.equals("favorite_do_success")){
                    Toasty.success(getApplication(),getString(R.string.discuz_api_message_template,messageResult.key,messageResult.content),Toast.LENGTH_LONG).show();
                }
                else if(!favorite && key.equals("do_success")){
                    Toasty.success(getApplication(),getString(R.string.discuz_api_message_template,messageResult.key,messageResult.content),Toast.LENGTH_LONG).show();
                }
                else {
                    Toasty.warning(getApplication(),getString(R.string.discuz_api_message_template,messageResult.key,messageResult.content),Toast.LENGTH_LONG).show();
                }
            }
            else {
                if(favorite){
                    Toasty.success(getApplication(),getString(R.string.favorite),Toast.LENGTH_SHORT).show();

                }
                else {
                    Toasty.success(getApplication(),getString(R.string.unfavorite),Toast.LENGTH_SHORT).show();
                }
                VibrateUtils.vibrateSlightly(getApplication());
            }


        }
    }

}
