package com.piusvelte.dirigible.home;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.piusvelte.dirigible.BuildConfig;
import com.piusvelte.dirigible.drive.Video;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Created by bemmanuel on 3/5/17.
 */

public class VideoUtils {

    private static final String TAG = VideoUtils.class.getSimpleName();

    private VideoUtils() {
        // not instantiable
    }

    public static boolean isVideo(String name) {
        return !TextUtils.isEmpty(name) && name.endsWith(".mp4");
    }

    public static String getNameFromPath(String path) {
        int index = path.lastIndexOf("/");

        try {
            if (index < 0) return URLDecoder.decode(path, "UTF-8");
            return URLDecoder.decode(path.substring(index + 1), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "error decoding: " + path, e);
            }
        }

        return path;
    }

    public static String getPath(String path, String name) {
        return String.format("%s/%s", path, name);
    }

    public static String getIconPath(String path, String name) {
        return String.format("%s/%s.jpg", path, name);
    }

    public static MediaInfo buildMediaInfo(String path, String name) {
        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        metadata.putString(MediaMetadata.KEY_TITLE, name);
        metadata.putString(MediaMetadata.KEY_SUBTITLE, name);

        Uri imageUrl = Uri.parse(getIconPath(path, name));
        WebImage image = new WebImage(imageUrl);

        // notification
        metadata.addImage(image);
        // lockscreen
        metadata.addImage(image);

        return new MediaInfo.Builder(getPath(path, name))
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(Video.MIME_TYPE_MP4)
                .setMetadata(metadata)
                .build();
    }
}
