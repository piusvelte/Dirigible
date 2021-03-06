package com.piusvelte.dirigible.drive;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.piusvelte.dirigible.BuildConfig;
import com.piusvelte.dirigible.Player;
import com.piusvelte.dirigible.util.BaseAsyncTaskLoader;
import com.piusvelte.dirigible.util.CredentialProvider;

import java.io.IOException;

/**
 * @author bemmanuel
 * @since 3/21/16
 */
public class MediaInfoLoader extends BaseAsyncTaskLoader<MediaInfo> {

    private static final String TAG = MediaInfoLoader.class.getSimpleName();

    public static class Callbacks implements LoaderManager.LoaderCallbacks<MediaInfo> {

        private static final String TAG = Callbacks.class.getSimpleName();
        private static final String ARG_VIDEO = TAG + ":args:video";

        @NonNull
        private Context mContext;
        @NonNull
        private CredentialProvider mCredentialProvider;
        @NonNull
        private Player mPlayer;
        private final int mLoaderId;

        public Callbacks(@NonNull Context context, @NonNull CredentialProvider credentialProvider, @NonNull Player player, int loaderId) {
            mContext = context;
            mCredentialProvider = credentialProvider;
            mPlayer = player;
            mLoaderId = loaderId;
        }

        public void init(@NonNull LoaderManager loaderManager, @NonNull Video video) {
            Bundle arguments = new Bundle(1);
            arguments.putParcelable(ARG_VIDEO, video);
            loaderManager.initLoader(mLoaderId, arguments, this);
        }

        public void restart(@NonNull LoaderManager loaderManager, @NonNull Video video) {
            Bundle arguments = new Bundle(1);
            arguments.putParcelable(ARG_VIDEO, video);
            loaderManager.restartLoader(mLoaderId, arguments, this);
        }

        @Override
        public Loader<MediaInfo> onCreateLoader(int id, Bundle args) {
            if (id == mLoaderId) {
                Video video = args != null ? (Video) args.getParcelable(ARG_VIDEO) : null;
                if (video == null) return null;
                return new MediaInfoLoader(mContext, video, mCredentialProvider.getCredential());
            }

            return null;
        }

        @Override
        public void onLoadFinished(Loader<MediaInfo> loader, MediaInfo data) {
            if (loader.getId() == mLoaderId) {
                mPlayer.onPlayVideo(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<MediaInfo> loader) {
            // NOOP
        }
    }

    @NonNull
    private Video mVideo;
    @NonNull
    private GoogleAccountCredential mGoogleAccountCredential;

    public MediaInfoLoader(@NonNull Context context, @NonNull Video video, @NonNull GoogleAccountCredential googleAccountCredential) {
        super(context);
        mVideo = video;
        mGoogleAccountCredential = googleAccountCredential;
    }

    @Override
    public MediaInfo loadInBackground() {

        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        metadata.putString(MediaMetadata.KEY_TITLE, mVideo.name);
        metadata.putString(MediaMetadata.KEY_SUBTITLE, mVideo.name);

        // access token
        String accessToken = null;

        try {
            accessToken = mGoogleAccountCredential.getToken();
        } catch (IOException | GoogleAuthException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error getting access token", e);
        }

        if (!TextUtils.isEmpty(mVideo.icon)) {
            Uri imageUrl = Uri.parse(mVideo.icon + "&access_token=" + accessToken);
            WebImage image = new WebImage(imageUrl);

            // notification
            metadata.addImage(image);
            // lockscreen
            metadata.addImage(image);
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "video: " + mVideo.url + "&access_token=" + accessToken);
        }

        return new MediaInfo.Builder(mVideo.url + "&access_token=" + accessToken)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(Video.MIME_TYPE_MP4)
                .setMetadata(metadata)
                .build();
    }
}
