//----------------------------------------------------
//
// Filename:    AudioFileListAdapter.java
// Author:      Daniel Walther
// Brief:       Controls the dynamic updating of
//              items in the main RecyclerView
//
//----------------------------------------------------

package com.example.audiophileplacebotest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class AudioFileListAdapter extends RecyclerView.Adapter<AudioFileListAdapter.AudioFileViewHolder>
{
    private static final String LOG_TAG = AudioFileListAdapter.class.getSimpleName();

    private final LayoutInflater mInflater;
    private final ArrayList<AudioFile> mAudioFileList;
    public final Context mContext;

    // Used to implement onClick in MainActivity
    public interface ListItemCallbacks
    {
        void onListItemDeleted(int index, AudioFileViewHolder holder);
        void onDragButtonTouched(AudioFileViewHolder holder, MotionEvent event);
        void onItemChecked(int index, AudioFileViewHolder holder);
        void onItemAdded(int index, AudioFileViewHolder holder);
    }

    private static ListItemCallbacks mListItemCallbacks;

    class AudioFileViewHolder extends RecyclerView.ViewHolder
    {
        private final ImageButton buttonPlayPause;
        private final SeekBar seekBarPosition;
        private final TextView textViewFilename;
        private final TextView textViewFileProperties;
        private final TextView textViewTotalTime;
        private final TextView textViewCurrentTime;
        private final ImageButton buttonDelete;
        private final ImageButton dragHandle;
        protected final RadioButton radioSelected;

        protected MediaPlayer mPlayer;
        private final Handler handler;

        Context mContext;

        AudioFileListAdapter mAdapter;

        public AudioFileViewHolder(View itemView, AudioFileListAdapter adapter, Context context)
        {
            super(itemView);
            mContext = context;
            mAdapter = adapter;
            handler = new Handler(mContext.getMainLooper());

            buttonPlayPause = itemView.findViewById(R.id.buttonPlayPause);
            seekBarPosition = itemView.findViewById(R.id.seekBarPosition);
            textViewFilename = itemView.findViewById(R.id.textViewFilename);
            textViewFileProperties = itemView.findViewById(R.id.textViewFileProperties);
            textViewTotalTime = itemView.findViewById(R.id.textViewTotalTime);
            textViewCurrentTime = itemView.findViewById(R.id.textViewCurrentTime);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
            dragHandle = itemView.findViewById(R.id.dragHandle);
            radioSelected = itemView.findViewById(R.id.radioSelected);

            mPlayer = new MediaPlayer();
            Log.d(LOG_TAG, "New MediaPlayer created.");

            // region Listeners
            // Called when user clicks play/pause button
            buttonPlayPause.setOnClickListener(v ->
            {
                // Switch from playing/not playing
                if (mPlayer.isPlaying())
                {
                    mPlayer.pause();
                    buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_play));
                }
                else
                {
                    mPlayer.start();
                    buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_pause));
                    updateSeekBar();
                }
            });

            // Called when user clicks delete button
            buttonDelete.setOnClickListener(v ->
            {
                // Stop media player
                mPlayer.stop();

                // Handle list updating in MainActivity
                mListItemCallbacks.onListItemDeleted(getAdapterPosition(), this);
            });

            // Called when user seeks to a new time
            seekBarPosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                {
                    if (fromUser)
                        mPlayer.seekTo(progress);

                    textViewCurrentTime.setText(MainActivity.formatter.format(mPlayer.getCurrentPosition()));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Change to play button when playback finishes
            mPlayer.setOnCompletionListener(mp -> buttonPlayPause.setImageDrawable
                    (ContextCompat.getDrawable(mContext, R.drawable.ic_play)));

            // Allows user to reorder list with dragButton
            dragHandle.setOnTouchListener((v, event) ->
            {
                mListItemCallbacks.onDragButtonTouched(this, event);
                return false;
            });

            // Change audio track with global controls enabled
            radioSelected.setOnClickListener(v ->
            {
                if (radioSelected.isChecked())
                    mListItemCallbacks.onItemChecked(getAdapterPosition(), this);
            });
            // endregion
        }

        protected void updateSeekBar()
        {
            seekBarPosition.setProgress(mPlayer.getCurrentPosition());
            textViewCurrentTime.setText(MainActivity.formatter.format(mPlayer.getCurrentPosition()));

            // Update seekbar every second while playing
            handler.removeCallbacksAndMessages(null);
            if (mPlayer.isPlaying())
                handler.postDelayed(this::updateSeekBar, MainActivity.UPDATE_RATE_MILLIS);
        }
    }

    public AudioFileListAdapter(Context context, ArrayList<AudioFile> audioFileList, ListItemCallbacks listener)
    {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mAudioFileList = audioFileList;
        mListItemCallbacks = listener;
    }

    @NonNull
    @Override
    public AudioFileListAdapter.AudioFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View mItemView = mInflater.inflate(R.layout.audiofilelist_item, parent, false);
        return new AudioFileViewHolder(mItemView, this, mContext);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioFileListAdapter.AudioFileViewHolder holder, int position)
    {
        AudioFile mCurrent = mAudioFileList.get(position);
        Uri uri = mCurrent.getUriPath();

        Log.d(LOG_TAG, "onBindViewHolder()");

        // Set source to new file
        try
        {
            holder.mPlayer.reset();
            holder.mPlayer.setDataSource(mContext, uri);
            holder.mPlayer.prepare();
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "onBindViewHolder ERROR: " + e.getMessage());
        }

        // Change views to exclude separate controls if global controls enabled
        if (mCurrent.isGlobalControlsEnabled())
        {
            holder.radioSelected.setVisibility(View.VISIBLE);
            holder.buttonPlayPause.setVisibility(View.GONE);
            holder.seekBarPosition.setVisibility(View.GONE);

            // Mute player if separate controls enabled
            holder.mPlayer.setVolume(0, 0);

            // Select first element automatically
            holder.radioSelected.setChecked(position == 0);
        }
        else
        {
            holder.radioSelected.setVisibility(View.GONE);
            holder.radioSelected.setChecked(false);
            holder.buttonPlayPause.setVisibility(View.VISIBLE);
            holder.seekBarPosition.setVisibility(View.VISIBLE);

            // Unmute player
            holder.mPlayer.setVolume(1, 1);
        }

        // Set initial controls/seekbar/timer values
        holder.seekBarPosition.setMax(holder.mPlayer.getDuration());
        holder.seekBarPosition.setProgress(0);
        String totalTime = "/  " + MainActivity.formatter.format(holder.mPlayer.getDuration());
        holder.textViewTotalTime.setText(totalTime);
        holder.buttonPlayPause.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_play));

        // Filename/properties
        String fileProperties;
        String filename;

        // Lossy compression
        if (mCurrent.getBitdepth() == null)
        {
            fileProperties = mCurrent.getFileformat() +
                    " " +
                    mCurrent.getSamplerate() +
                    ", " +
                    mCurrent.getBitrate();
        }
        // Lossless/lossless compression
        else
        {
            fileProperties = mCurrent.getFileformat() +
                    " " +
                    mCurrent.getBitdepth() +
                    " @ " +
                    mCurrent.getSamplerate();
        }

        // Don't show filename/properties if hidden
        if (mCurrent.isHidden())
        {
            // Set filename so as not to reveal file itself
            filename = mContext.getString(R.string.hiddenFilename) + (position + 1);

            // Hide properties
            holder.textViewFileProperties.setVisibility(View.GONE);
        }
        else
        {
            // Show filename
            filename = mCurrent.getFilename();

            // Hide properties
            holder.textViewFileProperties.setVisibility(View.VISIBLE);
        }

        holder.textViewFilename.setText(filename);
        holder.textViewFileProperties.setText(fileProperties);

        mListItemCallbacks.onItemAdded(position, holder);
    }

    @Override
    public int getItemCount()
    {
        return mAudioFileList.size();
    }
}
