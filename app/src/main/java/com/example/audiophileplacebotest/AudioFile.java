//--------------------------------------------------
//
// Filename:    AudioFIle.java
// Author:      Daniel Walther
// Brief:       Stores URI and properties of an
//              audio file in user's storage.
//              Used as a model for the main
//              RecyclerView.
//
//--------------------------------------------------

package com.example.audiophileplacebotest;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.util.Locale;

public class AudioFile
{
    private static final String LOG_TAG = AudioFile.class.getSimpleName();

    private final Uri uriPath;
    private String filename;
    private String fileformat;

    private String bitdepth;
    private String samplerate;
    private String bitrate;

    // View-specific members
    private boolean isHidden;
    private boolean globalControlsEnabled;

    public AudioFile(Uri uriPath_, Context context)
    {
        uriPath = uriPath_;
        isHidden = false;

        try
        {
            // Get filename
            filename = DocumentFile.fromSingleUri(context, uriPath_).getName();

            // Determine filetype from extension
            if (filename != null)
                fileformat = filename.substring(filename.lastIndexOf('.') + 1).toUpperCase();
            else
                fileformat = filename = "UNKNOWN";

            // Determine file type by first four bytes
            InputStream is = context.getContentResolver().openInputStream(uriPath_);
            byte[] fileTypeBytes = new byte[4];
            is.read(fileTypeBytes, 0, 4);
            StringBuilder fileTypeSB = new StringBuilder();
            for (byte b : fileTypeBytes)
                fileTypeSB.append((char) b);

            // Custom property searching for RIFF (WAV) and FLAC
            if ("RIFF".equals(fileTypeSB.toString()))
            {
                // Fileformat is wav, regardless of extension
                fileformat = "WAV";

                // Read samplerate from file header
                byte[] SRBytes = new byte[4];
                is.skip(20);
                is.read(SRBytes, 0, 4);

                // Format samplerate
                float sRate = bytesToInt(SRBytes) / 1000f;
                if (sRate == (int) sRate)
                    samplerate = String.format(Locale.getDefault(), "%d kHz", (int) sRate);
                else
                    samplerate = String.format(Locale.getDefault(), "%s kHz", sRate);

                // Read bitdepth from file header
                byte[] BDBytes = new byte[2];
                is.skip(6);
                is.read(BDBytes, 0, 2);

                // Format bitdepth
                bitdepth = bytesToInt(BDBytes) + "-bit";
            }
            else if ("fLaC".equals(fileTypeSB.toString()))
            {
                // Fileformat is flac, regardless of extension
                fileformat = "FLAC";

                // Read samplerate from file header
                is.skip(14);
                byte[] SRBytes = new byte[3];
                is.read(SRBytes, 0, 3);

                // Apparently FLAC allows multiple values to overlap in a single byte
                int SRInt = 0;
                SRInt |= SRBytes[0] & 0xFF;
                SRInt <<= 8;
                SRInt |= SRBytes[1] & 0xFF;
                SRInt <<= 4;
                SRInt |= (SRBytes[2] >> 4) & 0xF;

                // Format samplerate
                float sRate = SRInt / 1000f;
                if (sRate == (int) sRate)
                    samplerate = String.format(Locale.getDefault(), "%d kHz", (int) sRate);
                else
                    samplerate = String.format(Locale.getDefault(), "%s kHz", sRate);

                // Read last four bits of bitrate from file header
                int BRInt = is.read();
                BRInt >>= 4;

                // Least significant bit for bitdepth is stored at the end of the last samplerate byte
                // Put the singular bit back in its place
                if ((SRBytes[2] & 1) == 1)
                    BRInt |= 1 << 4;
                else
                    BRInt &= ~(1 << 4);

                // Bitdepth is stored as (bitdepth - 1) in FLAC
                // Just so they can support 32-bit with only 5 bits
                // of storage I guess?
                ++BRInt;

                bitdepth = BRInt + "-bit";
            }
            else
            {
                // Use MediaExtractor/MediaFormat for file properties
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(context, uriPath_, null);
                MediaFormat mf = extractor.getTrackFormat(0);

                //Get bitrate/samplerate from MediaFormat
                bitrate = mf.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 + " kbps";
                samplerate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE) / 1000f + " kHz";
            }

            is.close();
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Converts an array of bytes into an int via bitwise operations
    private static int bytesToInt(byte[] bytes)
    {
        if (bytes.length > 4)
            throw new IllegalArgumentException("Byte array has a max length of 4.");

        int ret = 0;
        ret |= bytes[bytes.length - 1] & 0xFF;

        for (int i = bytes.length - 2; i >= 0; --i)
        {
            ret <<= 8;
            ret |= bytes[i] & 0xFF; // Need to mask in order to only OR the first
                                    // 8 bits because java be like that
        }

        return ret;
    }

    // region Getters/Setters
    public Uri getUriPath()
    {
        return uriPath;
    }

    public String getFilename()
    {
        return filename;
    }

    public String getFileformat()
    {
        return fileformat;
    }

    public String getSamplerate()
    {
        return samplerate;
    }

    public String getBitrate()
    {
        return bitrate;
    }

    public String getBitdepth()
    {
        return bitdepth;
    }

    public boolean isHidden()
    {
        return isHidden;
    }

    public void setHidden(boolean hidden)
    {
        isHidden = hidden;
    }

    public boolean isGlobalControlsEnabled()
    {
        return globalControlsEnabled;
    }

    public void setGlobalControlsEnabled(boolean globalControlsEnabled_)
    {
        globalControlsEnabled = globalControlsEnabled_;
    }
    // endregion
}