package com.piusvelte.dirigible.video;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.piusvelte.dirigible.BuildConfig;
import com.piusvelte.dirigible.util.BaseAsyncTaskLoader;
import com.piusvelte.dirigible.util.CredentialProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author bemmanuel
 * @since 3/9/16
 */
public class LibraryLoader extends BaseAsyncTaskLoader<LibraryLoader.Result> {

    private static final String TAG = LibraryLoader.class.getSimpleName();

    public interface Viewer {
        void onLibraryLoaded(@NonNull LibraryLoader.Result result);
    }

    public static class Callbacks implements LoaderManager.LoaderCallbacks<LibraryLoader.Result> {

        private static final String TAG = Callbacks.class.getSimpleName();
        private static final String ARG_NEXT_PAGE_TOKEN = TAG + ":args:nextPageToken";
        private static final String ARG_QUERY = TAG + ":args:query";

        @NonNull
        private Context mContext;
        @NonNull
        private CredentialProvider mCredentialProvider;
        @NonNull
        private Viewer mViewer;
        private final int mLoaderId;

        public Callbacks(@NonNull Context context, @NonNull CredentialProvider credentialProvider, @NonNull Viewer viewer, int loaderId) {
            mContext = context;
            mCredentialProvider = credentialProvider;
            mViewer = viewer;
            mLoaderId = loaderId;
        }

        @NonNull
        private Bundle getArguments(@Nullable String query, @Nullable String nextPageToken) {
            Bundle arguments = new Bundle(2);
            arguments.putString(ARG_NEXT_PAGE_TOKEN, nextPageToken);
            arguments.putString(ARG_QUERY, query);
            return arguments;
        }

        public void load(@NonNull LoaderManager loaderManager, @Nullable String query, @Nullable String nextPageToken) {
            loaderManager.initLoader(mLoaderId, getArguments(query, nextPageToken), this);
        }

        public void reload(@NonNull LoaderManager loaderManager, @Nullable String query, @Nullable String nextPageToken) {
            loaderManager.restartLoader(mLoaderId, getArguments(query, nextPageToken), this);
        }

        @Override
        public Loader<LibraryLoader.Result> onCreateLoader(int id, Bundle args) {
            if (id == mLoaderId) {
                String query = args.getString(ARG_QUERY);
                String nextPageToken = args.getString(ARG_NEXT_PAGE_TOKEN);
                return new LibraryLoader(mContext, mCredentialProvider.getCredential(), query, nextPageToken);
            }

            return null;
        }

        @Override
        public void onLoadFinished(Loader<LibraryLoader.Result> loader, LibraryLoader.Result data) {
            if (loader.getId() == mLoaderId) {
                mViewer.onLibraryLoaded(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<LibraryLoader.Result> loader) {
            // NOOP
        }
    }

    @Nullable
    private static Drive sDrive;

    @NonNull
    private GoogleAccountCredential mGoogleAccountCredential;

    @Nullable
    private final String mQuery;
    @Nullable
    private final String mNextPageToken;

    public LibraryLoader(@NonNull Context context, @NonNull GoogleAccountCredential googleAccountCredential, @Nullable String query, @Nullable String nextPageToken) {
        super(context);
        mGoogleAccountCredential = googleAccountCredential;
        mQuery = query;
        mNextPageToken = nextPageToken;
    }

    @Override
    public Result loadInBackground() {
        Result result = new Result();

        if (TextUtils.isEmpty(mGoogleAccountCredential.getSelectedAccountName())) {
            return result;
        }

        Drive drive = getDrive(mGoogleAccountCredential);

        try {
            FileList folderList = drive.files().list()
                    .setQ("'root' in parents and mimeType='application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(result.nextPageToken)
                    .execute();

            List<File> folders = folderList.getFiles();

            for (File folder : folders) {
                if ("Videos".equals(folder.getName())) {
                    StringBuilder query = new StringBuilder("'")
                            .append(folder.getId())
                            .append("' in parents and (mimeType='")
                            .append(Video.MIME_TYPE_MP4)
                            .append("' or mimeType='")
                            .append(Video.MIME_TYPE_JPEG)
                            .append("')");

                    if (!TextUtils.isEmpty(mQuery)) {
                        query.append(" and name contains '")
                                .append(mQuery)
                                .append("'");
                    }

                    FileList videosList = drive.files().list()
                            .setQ(query.toString())
                            .setOrderBy("name")
                            .setSpaces("drive")
                            .setFields("nextPageToken, files(id, name, mimeType)")
                            .setPageToken(mNextPageToken)
                            .execute();

                    result.videos = assembleVideos(videosList.getFiles(), drive);
                    result.nextPageToken = videosList.getNextPageToken();
                }
            }
        } catch (UserRecoverableAuthIOException e) {
            result.authorizationIntent = e.getIntent();
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "error getting file list", e);
            }
        }

        return result;
    }

    private List<Video> assembleVideos(List<File> fileList, @NonNull Drive drive) {
        List<Video> videos = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>(fileList);

        // get videos
        Iterator<File> videosIterator = files.iterator();

        while (videosIterator.hasNext()) {
            File file = videosIterator.next();

            if (Video.MIME_TYPE_MP4.equals(file.getMimeType())) {
                Video video = new Video(file.getId(), file.getName().split("\\.")[0]);

                try {
                    HttpRequest request = drive.getRequestFactory().buildGetRequest(drive.files()
                            .get(file.getId())
                            .set("alt", "media")
                            .buildHttpRequestUrl());
                    video.url = request.getUrl().build();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "error getting url", e);
                }

                videos.add(video);
                videosIterator.remove();
            }
        }

        // get thumbnails
        for (Video video : videos) {
            Iterator<File> thumbnailsIterator = files.iterator();

            while (thumbnailsIterator.hasNext()) {
                File file = thumbnailsIterator.next();

                if (Video.MIME_TYPE_JPEG.equals(file.getMimeType())
                        && video.name.equals(file.getName().split("\\.")[0])) {

                    try {
                        HttpRequest request = drive.getRequestFactory().buildGetRequest(drive.files()
                                .get(file.getId())
                                .set("alt", "media")
                                .buildHttpRequestUrl());
                        video.icon = request.getUrl().build();
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "error getting url", e);
                    }

                    thumbnailsIterator.remove();
                }
            }
        }

        return videos;
    }

    @NonNull
    private static Drive getDrive(@NonNull GoogleAccountCredential credential) {
        if (sDrive == null) {
            sDrive = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                    .build();
        }

        return sDrive;
    }

    public static class Result {
        @Nullable
        public List<Video> videos;
        @Nullable
        public Intent authorizationIntent;
        @Nullable
        public String nextPageToken;
    }
}
