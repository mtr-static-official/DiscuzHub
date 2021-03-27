package com.kidozh.discuzhub.activities;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.kidozh.discuzhub.R;
import com.kidozh.discuzhub.adapter.ThreadDraftAdapter;
import com.kidozh.discuzhub.callback.recyclerViewSwipeToDeleteCallback;
import com.kidozh.discuzhub.database.ThreadDraftDatabase;
import com.kidozh.discuzhub.databinding.ActivityViewThreadDraftBinding;
import com.kidozh.discuzhub.entities.ThreadDraft;
import com.kidozh.discuzhub.entities.Discuz;
import com.kidozh.discuzhub.entities.User;
import com.kidozh.discuzhub.utilities.AnimationUtils;
import com.kidozh.discuzhub.utilities.ConstUtils;

import java.util.List;

import es.dmoral.toasty.Toasty;

public class ThreadDraftActivity extends BaseStatusActivity implements recyclerViewSwipeToDeleteCallback.onRecyclerviewSwiped{
    private final String TAG = ThreadDraftActivity.class.getSimpleName();


    ThreadDraftAdapter threadDraftAdapter;
    LiveData<List<ThreadDraft>> listLiveData;
    ActivityViewThreadDraftBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewThreadDraftBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        configureIntentData();
        configureActionBar();
        configureRecyclerview();


    }

    private void configureIntentData(){
        Intent intent = getIntent();
        bbsInfo = (Discuz) intent.getSerializableExtra(ConstUtils.PASS_BBS_ENTITY_KEY);
        user = (User) intent.getSerializableExtra(ConstUtils.PASS_BBS_USER_KEY);
    }

    private void configureActionBar(){
        setSupportActionBar(binding.toolbar);
        binding.toolbarTitle.setText(getString(R.string.bbs_draft_box));
        binding.toolbarSubtitle.setText(bbsInfo.site_name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void configureRecyclerview(){
        binding.bbsShowThreadDraftRecyclerview.setLayoutManager(new LinearLayoutManager(this));
        threadDraftAdapter = new ThreadDraftAdapter(bbsInfo, user);
        binding.bbsShowThreadDraftRecyclerview.setItemAnimator(AnimationUtils.INSTANCE.getRecyclerviewAnimation(this));
        binding.bbsShowThreadDraftRecyclerview.setAdapter(AnimationUtils.INSTANCE.getAnimatedAdapter(this,threadDraftAdapter));
        listLiveData = ThreadDraftDatabase.getInstance(this)
                .getbbsThreadDraftDao()
                .getAllThreadDraftByBBSId(bbsInfo.getId());
        listLiveData.observe(this, new Observer<List<ThreadDraft>>() {
            @Override
            public void onChanged(List<ThreadDraft> ThreadDrafts) {
                if(ThreadDrafts !=null && ThreadDrafts.size()!=0){
                    threadDraftAdapter.setThreadDraftList(ThreadDrafts);
                    binding.bbsShowThreadDraftNoItemFound.setVisibility(View.GONE);
                }
                else {
                    binding.bbsShowThreadDraftNoItemFound.setVisibility(View.VISIBLE);
                    threadDraftAdapter.setThreadDraftList(ThreadDrafts);
                    threadDraftAdapter.notifyDataSetChanged();
                }

            }
        });
        // swipe to delete support
        recyclerViewSwipeToDeleteCallback swipeToDeleteUserCallback = new recyclerViewSwipeToDeleteCallback(this,threadDraftAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeToDeleteUserCallback);
        itemTouchHelper.attachToRecyclerView(binding.bbsShowThreadDraftRecyclerview);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            this.finishAfterTransition();
            return false;
        }
        else if(item.getItemId() == R.id.bbs_draft_nav_menu_sort){
            return false;
        }
        else if(item.getItemId() == R.id.bbs_draft_nav_menu_swipe_delte){
            showDeleteAllDraftDialog();
            return false;
        }
        else {
            return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bbs_draft_nav_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showDeleteAllDraftDialog(){
        if(threadDraftAdapter.getThreadDraftList() == null || threadDraftAdapter.getThreadDraftList().size() == 0){
            Toasty.info(this,getString(R.string.bbs_thread_draft_empty),Toast.LENGTH_SHORT).show();
        }
        else {
            AlertDialog alertDialogs = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.bbs_delete_all_draft))
                    .setMessage(getString(R.string.bbs_delete_all_drafts_alert,bbsInfo.site_name))
                    .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new deleteAllThreadDraftTask(getApplicationContext()).execute();
                        }
                    })

                    .create();
            alertDialogs.show();

        }
    }

    @Override
    public void onSwiped(int position, int direction) {
        Log.d(TAG,"On swiped "+position + direction);
        List<ThreadDraft> threadDraftList = threadDraftAdapter.getThreadDraftList();
        ThreadDraft deleteThreadDraft = threadDraftList.get(position);
        new deleteThreadDraftTask(this,deleteThreadDraft).execute();
    }

    public class addThreadDraftTask extends AsyncTask<Void, Void, Void> {
        private ThreadDraft insertThreadDraft;
        private Context context;
        private Boolean saveThenFinish = false;
        public addThreadDraftTask(Context context, ThreadDraft threadDraft ){
            this.insertThreadDraft = threadDraft;
            this.context = context;
        }
        @Override
        protected Void doInBackground(Void... voids) {
            long inserted = ThreadDraftDatabase
                    .getInstance(context)
                    .getbbsThreadDraftDao().insert(insertThreadDraft);
            insertThreadDraft.setId( (int) inserted);
            Log.d(TAG, "add forum into database"+insertThreadDraft.subject+insertThreadDraft.getId());

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }

    public class deleteAllThreadDraftTask extends AsyncTask<Void, Void, Void> {
        private Context context;
        public deleteAllThreadDraftTask(Context context){
            this.context = context;
        }
        @Override
        protected Void doInBackground(Void... voids) {
            ThreadDraftDatabase
                    .getInstance(context)
                    .getbbsThreadDraftDao().deleteAllForumInformation(bbsInfo.getId());

            Log.d(TAG, "delete all forum from database");

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }

    public class deleteThreadDraftTask extends AsyncTask<Void, Void, Void> {
        private ThreadDraft threadDraft;
        private Context context;
        public deleteThreadDraftTask(Context context, ThreadDraft threadDraft ){
            this.threadDraft = threadDraft;
            this.context = context;
        }
        @Override
        protected Void doInBackground(Void... voids) {
            ThreadDraftDatabase
                    .getInstance(context)
                    .getbbsThreadDraftDao().delete(threadDraft);
            Log.d(TAG, "delete forum into database"+threadDraft.subject+threadDraft.getId());

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // show undo dialog
            showUndoSnackbar(threadDraft);
        }
    }

    public void showUndoSnackbar(final ThreadDraft threadDraft) {
        View view = findViewById(R.id.bbs_show_thread_draft_coordinatorlayout);
        Snackbar snackbar = Snackbar.make(view, getString(R.string.bbs_delete_draft,threadDraft.subject,bbsInfo.site_name),
                Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.bbs_undo_delete, new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                undoDeleteDraft(threadDraft);
            }
        });
        snackbar.show();
    }

    private void undoDeleteDraft(final ThreadDraft threadDraft){
        new addThreadDraftTask(this,threadDraft).execute();

    }


}
