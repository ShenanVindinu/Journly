package uk.ac.wlv.journly;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;

public class ImageUtils {

    // Shrinks an image file in place so its longest side is at most maxSize pixels
    public static void shrink(File file, int maxSize) {
        String path = file.getAbsolutePath();
        try {
            // First pass: read only the size, not the pixels
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return; // not an image
            }

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSize) {
                sample *= 2;
            }

            // Second pass: decode a smaller version
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap == null) {
                return;
            }

            // Camera photos can be stored sideways, so rotate them upright
            int degrees = readRotation(path);
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
        } catch (Exception e) {
            // If shrinking fails we simply keep the original file
        }
    }

    private static int readRotation(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}