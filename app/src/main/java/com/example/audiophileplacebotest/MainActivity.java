//---------------------------------------------------------
//
// Filename:    MainActivity.java
// Author:      Daniel Walther
// Brief:       Controls the logic and interactivity
//              of the Main Activity page
//
//---------------------------------------------------------

package com.example.audiophileplacebotest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AudioFileListAdapter.ListItemCallbacks
{
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    protected static final int SELECT_FILE = 10;
    private static final int SEEK_AMOUNT_MILLIS = 5000;

    private static boolean alreadyLoaded = false; // Keeps track of whether to show sample audio files
    private final int MAX_LOADED_AUDIO_FILES = 5;
    private static ArrayList<AudioFile> audioFileList = new ArrayList<>();

    protected static final SimpleDateFormat formatter = new SimpleDateFormat("m:ss", Locale.getDefault());
    private Handler handler;
    protected static final int UPDATE_RATE_MILLIS = 250;

    private boolean filesHidden = false;

    private TextView textViewEmptyListIndicator;
    private Button buttonToggleHide;
    private ImageButton buttonPlayPause;
    private ImageButton buttonSeekForward;
    private ImageButton buttonSeekBackward;
    private SeekBar seekBarPosition;

    private CheckBox checkBoxGlobalControls;
    private int globalDuration = -1;

    private RecyclerView recyclerViewAudioFiles;
    private AudioFileListAdapter mAdapter;
    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbarMain));
        handler = new Handler(getMainLooper());

        // Init recyclerview components
        recyclerViewAudioFiles = findViewById(R.id.recyclerViewAudioFiles);
        mAdapter = new AudioFileListAdapter(this, audioFileList, this);
        recyclerViewAudioFiles.setAdapter(mAdapter);
        recyclerViewAudioFiles.setLayoutManager(new LinearLayoutManager(this));

        // Attach itemTouchHelper
        itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerViewAudioFiles);

        // Retrieve view references
        textViewEmptyListIndicator = findViewById(R.id.textViewEmptyListIndicator);
        buttonToggleHide = findViewById(R.id.buttonToggleHide);
        buttonPlayPause = findViewById(R.id.buttonPlayPause);
        buttonSeekForward = findViewById(R.id.buttonSeekForward);
        buttonSeekBackward = findViewById(R.id.buttonSeekBackward);
        seekBarPosition = findViewById(R.id.seekBarPosition);
        checkBoxGlobalControls = findViewById(R.id.checkBoxGlobalControls);

        // region Setting Listeners
        checkBoxGlobalControls.setOnCheckedChangeListener((v, checked) ->
        {
            if (checked)
            {
                int count = audioFileList.size();
                int firstDuration = ((AudioFileListAdapter.AudioFileViewHolder)
                        recyclerViewAudioFiles.findViewHolderForAdapterPosition(0)).mPlayer.getDuration();
                for (int i = 1; i < count; ++i)
                {
                    AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                            recyclerViewAudioFiles.findViewHolderForAdapterPosition(i);

                    // Durations must be within 0.3 seconds of each other
                    int diff = Math.abs(cur.mPlayer.getDuration() - firstDuration);
                    if (diff > 300)
                    {
                        checkBoxGlobalControls.setChecked(false);
                        Toast.makeText(this, R.string.toast_differingdurations,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Update list items
                for (AudioFile a : audioFileList)
                    a.setGlobalControlsEnabled(true);

                // Show controls
                buttonPlayPause.setVisibility(View.VISIBLE);
                buttonSeekForward.setVisibility(View.VISIBLE);
                buttonSeekBackward.setVisibility(View.VISIBLE);
                seekBarPosition.setVisibility(View.VISIBLE);

                // Play each audio file
                for (int i = 0; i < count; ++i)
                {
                    AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                            recyclerViewAudioFiles.findViewHolderForAdapterPosition(i);

                    if (cur.radioSelected.isChecked())
                        cur.mPlayer.setVolume(1, 1);
                    else
                        cur.mPlayer.setVolume(0, 0);
                }

                mAdapter.notifyDataSetChanged();
            }
            else
            {
                // Update list items
                for (AudioFile a : audioFileList)
                    a.setGlobalControlsEnabled(false);
                recyclerViewAudioFiles.post(() -> mAdapter.notifyDataSetChanged());

                // Hide controls
                buttonPlayPause.setVisibility(View.GONE);
                buttonSeekForward.setVisibility(View.GONE);
                buttonSeekBackward.setVisibility(View.GONE);
                seekBarPosition.setVisibility(View.GONE);
            }
        });

        buttonToggleHide.setOnClickListener(v ->
        {
            // Toggle from files hidden to files not hidden
            if (filesHidden)
            {
                // Unhide files/update
                for (AudioFile a : audioFileList)
                    a.setHidden(false);

                // Update button
                buttonToggleHide.setText(R.string.buttonToggleHide_filesNotHidden);
                filesHidden = false;
            }
            else
            {
                // Shuffle list
                Collections.shuffle(audioFileList);

                // Hide files/update
                for (AudioFile a : audioFileList)
                    a.setHidden(true);

                // Update button
                buttonToggleHide.setText(R.string.buttonToggleHide_filesHidden);
                filesHidden = true;
            }

            buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play));
            mAdapter.notifyDataSetChanged();
        });

        buttonPlayPause.setOnClickListener(v ->
        {
            AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.findViewHolderForAdapterPosition(0);

            if (first.mPlayer.isPlaying())
                pauseAudioGlobal();
            else
                playAudioGlobal();
        });

        buttonSeekForward.setOnClickListener(v -> seekOffsetGlobal(SEEK_AMOUNT_MILLIS));
        buttonSeekBackward.setOnClickListener(v -> seekOffsetGlobal(-SEEK_AMOUNT_MILLIS));

        seekBarPosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if (fromUser)
                {
                    AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                            recyclerViewAudioFiles.findViewHolderForAdapterPosition(0);

                    // Seek on all tracks
                    int offset = progress - first.mPlayer.getCurrentPosition();
                    seekOffsetGlobal(offset);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        // endregion

        // Load sample audio files when first opened
        if (!alreadyLoaded)
        {
            alreadyLoaded = true;

            Uri uri1 = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.nightingale_intro_lossless);
            Uri uri2 = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.nightingale_intro_192k);
            Uri uri3 = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.nightingale_intro_64k);
            loadAudioFromFile(uri1);
            loadAudioFromFile(uri2);
            loadAudioFromFile(uri3);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        // User has selected a file
        if (requestCode == SELECT_FILE && resultCode == Activity.RESULT_OK && data != null)
                loadAudioFromFile(Uri.parse(data.getDataString()));
    }

    private void loadAudioFromFile(Uri uri)
    {
        // Add new file
        AudioFile audioFile = new AudioFile(uri, this);
        if (checkBoxGlobalControls.isChecked())
            audioFile.setGlobalControlsEnabled(true);
        audioFileList.add(audioFile);
        mAdapter.notifyItemInserted(audioFileList.size());

        // Remove instruction text, show togglebutton/recyclerview
        checkBoxGlobalControls.setVisibility(View.VISIBLE);
        textViewEmptyListIndicator.setVisibility(View.INVISIBLE);
        recyclerViewAudioFiles.setVisibility(View.VISIBLE);
        buttonToggleHide.setVisibility(View.VISIBLE);
    }

    // region AppBar Methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        int id = item.getItemId();

        if (id == R.id.action_loadfile)
        {
            // Can't add files if files are currently hidden
            if (filesHidden)
                Toast.makeText(this, getString(R.string.toastUnhideFiles), Toast.LENGTH_SHORT).show();
            else
            {
                if (audioFileList.size() < MAX_LOADED_AUDIO_FILES)
                {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("audio/*");
                    startActivityForResult(intent, SELECT_FILE);
                }
                // Can't add files if limit is reached
                else
                {
                    String msg = String.format(Locale.getDefault(), getString(R.string.toastMaxFilesLoaded), MAX_LOADED_AUDIO_FILES);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }
    // endregion

    // region Playback controls
    private void updateSeekBarGlobal()
    {
        AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                recyclerViewAudioFiles.findViewHolderForAdapterPosition(0);

        if (first != null)
        {
            seekBarPosition.setProgress(first.mPlayer.getCurrentPosition());

            // Update seekbar every second while playing
            handler.removeCallbacksAndMessages(null);
            if (first.mPlayer.isPlaying())
                handler.postDelayed(this::updateSeekBarGlobal, UPDATE_RATE_MILLIS);
            else
                buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play));
        }
    }

    private void playAudioGlobal()
    {
        Log.d(LOG_TAG, "playAudioGlobal()");

        buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_pause));

        // Play each audio file
        int count = audioFileList.size();
        for (int i = 0; i < count; ++i)
        {
            AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.getChildViewHolder(recyclerViewAudioFiles.getChildAt(i));

            cur.mPlayer.start();
            cur.updateSeekBar();
        }

        updateSeekBarGlobal();
    }

    private void pauseAudioGlobal()
    {
        Log.d(LOG_TAG, "pauseAudioGlobal()");
        buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play));

        AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                recyclerViewAudioFiles.getChildViewHolder(recyclerViewAudioFiles.getChildAt(0));

        if (first == null)
            return;

        first.mPlayer.pause();

        // Pause each audio file
        int count = audioFileList.size();
        for (int i = 1; i < count; ++i)
        {
            AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.getChildViewHolder(recyclerViewAudioFiles.getChildAt(i));

            // Sync up tracks just in case something weird happened
            cur.mPlayer.seekTo(first.mPlayer.getCurrentPosition());
            cur.mPlayer.pause();
        }
    }

    // NOTE:
    // This method does not actually set each MediaPlayer's state to STOPPED.
    // It is used to pause playback and reset to the beginning of the tracks
    private void stopAudioGlobal()
    {
        Log.d(LOG_TAG, "stopAudioGlobal()");

        buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play));

        // Play each audio file
        int count = audioFileList.size();
        for (int i = 0; i < count; ++i)
        {
            AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.getChildViewHolder(recyclerViewAudioFiles.getChildAt(i));

            cur.mPlayer.seekTo(0);
            cur.mPlayer.pause();
        }
    }

    private void seekOffsetGlobal(int millisOffset)
    {
        AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                recyclerViewAudioFiles.findViewHolderForAdapterPosition(0);

        // Cannot skip before 0
        int newPos = Math.max(first.mPlayer.getCurrentPosition() + millisOffset, 0);

        // Stop audio if seeking past duration
        if (newPos > first.mPlayer.getDuration())
            stopAudioGlobal();
        else
        {
            // Seek to offset in each file
            int count = audioFileList.size();
            for (int i = 0; i < count; ++i)
            {
                AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                        recyclerViewAudioFiles.getChildViewHolder(recyclerViewAudioFiles.getChildAt(i));

                cur.mPlayer.seekTo(newPos);
            }
        }
    }
    // endregion

    // region ListItemCallbacks
    // Called when user deletes an item
    @Override
    public void onListItemDeleted(int index, AudioFileListAdapter.AudioFileViewHolder holder)
    {
        // Remove item
        audioFileList.remove(index);

        // Remove recyclerview/toggle button, show display instruction text if all items deleted
        if (audioFileList.size() == 0)
        {
            checkBoxGlobalControls.setChecked(false);
            textViewEmptyListIndicator.setVisibility(View.VISIBLE);
            checkBoxGlobalControls.setVisibility(View.GONE);
            recyclerViewAudioFiles.setVisibility(View.GONE);
            buttonToggleHide.setVisibility(View.GONE);

            buttonToggleHide.setText(R.string.buttonToggleHide_filesNotHidden);
            filesHidden = false;
        }
        else
        {
            AudioFileListAdapter.AudioFileViewHolder last = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.findViewHolderForAdapterPosition(audioFileList.size());

            if (last != null)
                globalDuration = last.mPlayer.getDuration();
        }

        // Update list
        mAdapter.notifyItemRemoved(index);
    }

    // Allows user to reorder items with dragbutton
    @Override
    public void onDragButtonTouched(AudioFileListAdapter.AudioFileViewHolder holder, MotionEvent event)
    {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
            itemTouchHelper.startDrag(holder);
    }

    @Override
    public void onItemChecked(int index, AudioFileListAdapter.AudioFileViewHolder holder)
    {
        // Deselect/mute other items
        int count = audioFileList.size();
        for (int i = 0; i < count; ++i)
        {
            if (i == index)
                continue;

            AudioFileListAdapter.AudioFileViewHolder cur = (AudioFileListAdapter.AudioFileViewHolder)
                    recyclerViewAudioFiles.findViewHolderForAdapterPosition(i);

            cur.mPlayer.setVolume(0, 0);
            cur.radioSelected.setChecked(false);
        }

        // Unmute
        holder.mPlayer.setVolume(1, 1);
    }

    @Override
    public void onItemAdded(int index, AudioFileListAdapter.AudioFileViewHolder holder)
    {
        // Store reference to shortest track if global controls enabled
        if (checkBoxGlobalControls.isChecked())
        {
            if (index == 0)
            {
                holder.mPlayer.setVolume(1, 1);

                // Update seekbar
                globalDuration = holder.mPlayer.getDuration();
                seekBarPosition.setMax(globalDuration);
            }
            else
            {
                // Global controls only enabled for tracks of same length (+/-0.3sec)
                int diff = Math.abs(holder.mPlayer.getDuration() - globalDuration);
                if (diff > 300)
                {
                    checkBoxGlobalControls.setChecked(false);
                    recyclerViewAudioFiles.post(this::stopAudioGlobal);
                    return;
                }

                AudioFileListAdapter.AudioFileViewHolder first = (AudioFileListAdapter.AudioFileViewHolder)
                        recyclerViewAudioFiles.findViewHolderForAdapterPosition(0);

                if (first != null)
                {
                    // Sync new track with those currently being played
                    holder.mPlayer.seekTo(first.mPlayer.getCurrentPosition());

                    if (first.mPlayer.isPlaying())
                    {
                        holder.mPlayer.start();
                        holder.updateSeekBar();
                    }
                }
            }
        }
    }
    // endregion

    ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END, 0)
    {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target)
        {
            int fromPos = viewHolder.getAdapterPosition();
            int targPos = target.getAdapterPosition();

            Collections.swap(audioFileList, fromPos, targPos);
            mAdapter.notifyItemMoved(fromPos, targPos);

            return false;
        }

        // User can only move items with dragbutton
        // (Not by long-pressing)
        @Override
        public boolean isLongPressDragEnabled()
        {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
    };
}