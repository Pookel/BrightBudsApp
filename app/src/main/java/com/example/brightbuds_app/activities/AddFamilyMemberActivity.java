package com.example.brightbuds_app.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.FamilyMemberAdapter;
import com.example.brightbuds_app.models.FamilyMember;
import com.example.brightbuds_app.services.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AddFamilyMemberActivity extends AppCompatActivity
        implements FamilyMemberAdapter.OnFamilyMemberClickListener {

    private static final int MAX_FAMILY_MEMBERS = 10;
    private static final int MAX_IMAGE_SIZE = 800;
    private static final int JPEG_QUALITY = 85;

    private ImageView imgFamilyPhoto;
    private ImageView btnPickImage;
    private EditText edtName;
    private EditText edtRelationship;
    private Button btnSave;
    private ImageView btnCloseIcon;
    private ImageView btnHomeIcon;
    private TextView txtCounter;
    private android.view.View photoContainer;

    private RecyclerView recyclerFamilyMembers;
    private FamilyMemberAdapter adapter;
    private final List<FamilyMember> familyList = new ArrayList<>();

    private DatabaseHelper dbHelper;
    private long currentParentId;

    private Bitmap currentBitmap = null;

    private long editingFamilyId = -1;
    private String editingImagePath = null;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_family_member);

        dbHelper = new DatabaseHelper(this);
        currentParentId = dbHelper.getCurrentParentLocalId();

        imgFamilyPhoto = findViewById(R.id.imgFamilyPhoto);
        btnPickImage = findViewById(R.id.btnPickImage);
        edtName = findViewById(R.id.edtName);
        edtRelationship = findViewById(R.id.edtRelationship);
        btnSave = findViewById(R.id.btnSave);
        btnCloseIcon = findViewById(R.id.btnCloseIcon);
        btnHomeIcon = findViewById(R.id.btnHomeIcon);
        txtCounter = findViewById(R.id.txtCounter);
        recyclerFamilyMembers = findViewById(R.id.recyclerFamilyMembers);
        photoContainer = findViewById(R.id.photoContainer);

        recyclerFamilyMembers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FamilyMemberAdapter(familyList, this);
        recyclerFamilyMembers.setAdapter(adapter);

        setupImageLaunchers();

        imgFamilyPhoto.setOnClickListener(v -> showImageSourceDialog());
        btnPickImage.setOnClickListener(v -> showImageSourceDialog());

        btnSave.setOnClickListener(v -> saveOrUpdateFamilyMember());

        btnCloseIcon.setOnClickListener(v -> finish());

        btnHomeIcon.setOnClickListener(v -> {
            Intent intent = new Intent(AddFamilyMemberActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        loadFamilyMembers();
        updateCounter();
        updateSaveEnabledState();
        startAvatarPulseAnimation();
    }

    private void setupImageLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            Bitmap raw = decodeSampledBitmapFromUri(uri, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
                            if (raw != null) {
                                Bitmap processed = processBitmapForAvatar(raw);
                                if (processed != null) {
                                    if (raw != processed) {
                                        raw.recycle();
                                    }
                                    currentBitmap = processed;
                                    imgFamilyPhoto.setImageBitmap(processed);
                                } else {
                                    raw.recycle();
                                    Toast.makeText(AddFamilyMemberActivity.this,
                                            "Could not process image",
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(AddFamilyMemberActivity.this,
                                        "Could not load image",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                new ActivityResultCallback<Bitmap>() {
                    @Override
                    public void onActivityResult(Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap processed = processBitmapForAvatar(bitmap);
                            if (processed != null) {
                                currentBitmap = processed;
                                imgFamilyPhoto.setImageBitmap(processed);
                            } else {
                                Toast.makeText(AddFamilyMemberActivity.this,
                                        "Could not process camera image",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    private void showImageSourceDialog() {
        String[] options = new String[]{"Take photo", "Choose from gallery", "Cancel"};
        new AlertDialog.Builder(this)
                .setTitle("Select photo source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        takePhotoLauncher.launch(null);
                    } else if (which == 1) {
                        pickImageLauncher.launch("image/*");
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void saveOrUpdateFamilyMember() {
        String firstName = edtName.getText().toString().trim();
        String relationship = edtRelationship.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) {
            edtName.setError("Name is required");
            return;
        }

        if (TextUtils.isEmpty(relationship)) {
            edtRelationship.setError("Relationship is required");
            return;
        }

        if (editingFamilyId == -1) {
            int existingCount = getFamilyMemberCountForParent(currentParentId);
            if (existingCount >= MAX_FAMILY_MEMBERS) {
                Toast.makeText(
                        this,
                        "You already added " + MAX_FAMILY_MEMBERS + " family members",
                        Toast.LENGTH_LONG
                ).show();
                updateSaveEnabledState();
                return;
            }

            if (currentBitmap == null) {
                Toast.makeText(this, "Please select or capture a picture", Toast.LENGTH_SHORT).show();
                return;
            }

            String imagePath = saveImageToInternalStorage(currentBitmap);
            if (imagePath == null) {
                Toast.makeText(this, "Could not save image", Toast.LENGTH_SHORT).show();
                return;
            }

            long rowId = insertFamilyMember(currentParentId, firstName, relationship, imagePath);
            if (rowId != -1) {
                Toast.makeText(this, "Family member saved", Toast.LENGTH_SHORT).show();
                clearForm();
                loadFamilyMembers();
                updateCounter();
                updateSaveEnabledState();
            } else {
                Toast.makeText(this, "Error saving family member", Toast.LENGTH_SHORT).show();
            }

        } else {
            String imagePathToSave;
            if (currentBitmap != null) {
                imagePathToSave = saveImageToInternalStorage(currentBitmap);
                if (imagePathToSave == null) {
                    Toast.makeText(this, "Could not save image", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                imagePathToSave = editingImagePath;
            }

            boolean ok = updateFamilyMember(editingFamilyId, currentParentId, firstName, relationship, imagePathToSave);
            if (ok) {
                Toast.makeText(this, "Family member updated", Toast.LENGTH_SHORT).show();
                clearForm();
                loadFamilyMembers();
                updateCounter();
                updateSaveEnabledState();
            } else {
                Toast.makeText(this, "Error updating family member", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearForm() {
        edtName.setText("");
        edtRelationship.setText("");
        currentBitmap = null;
        editingFamilyId = -1;
        editingImagePath = null;
        btnSave.setText("Save");
        imgFamilyPhoto.setImageResource(R.drawable.ic_child_avatar_placeholder);
    }

    private void updateCounter() {
        int count = getFamilyMemberCountForParent(currentParentId);
        String text = "Family members: " + count + " / " + MAX_FAMILY_MEMBERS;
        txtCounter.setText(text);
    }

    private void updateSaveEnabledState() {
        int count = getFamilyMemberCountForParent(currentParentId);
        btnSave.setEnabled(count < MAX_FAMILY_MEMBERS || editingFamilyId != -1);
    }

    private void loadFamilyMembers() {
        familyList.clear();
        List<FamilyMember> fromDb = dbHelper.getFamilyMembersForParent(currentParentId);
        familyList.addAll(fromDb);
        adapter.notifyDataSetChanged();
    }

    private int getFamilyMemberCountForParent(long parentId) {
        int count = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_FAMILY +
                        " WHERE " + DatabaseHelper.COL_FAMILY_PARENT_ID + " = ?",
                new String[]{String.valueOf(parentId)}
        );
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    private long insertFamilyMember(long parentId,
                                    String firstName,
                                    String relationship,
                                    String imagePath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_FAMILY_PARENT_ID, parentId);
        values.put(DatabaseHelper.COL_FAMILY_FIRST_NAME, firstName);
        values.put(DatabaseHelper.COL_FAMILY_RELATIONSHIP, relationship);
        values.put(DatabaseHelper.COL_FAMILY_IMAGE_PATH, imagePath);
        return db.insert(DatabaseHelper.TABLE_FAMILY, null, values);
    }

    private boolean updateFamilyMember(long familyId,
                                       long parentId,
                                       String firstName,
                                       String relationship,
                                       String imagePath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_FAMILY_PARENT_ID, parentId);
        values.put(DatabaseHelper.COL_FAMILY_FIRST_NAME, firstName);
        values.put(DatabaseHelper.COL_FAMILY_RELATIONSHIP, relationship);
        values.put(DatabaseHelper.COL_FAMILY_IMAGE_PATH, imagePath);

        int rows = db.update(
                DatabaseHelper.TABLE_FAMILY,
                values,
                DatabaseHelper.COL_FAMILY_ID + " = ?",
                new String[]{String.valueOf(familyId)}
        );
        return rows > 0;
    }

    private boolean deleteFamilyMember(long familyId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(
                DatabaseHelper.TABLE_FAMILY,
                DatabaseHelper.COL_FAMILY_ID + " = ?",
                new String[]{String.valueOf(familyId)}
        );
        return rows > 0;
    }

    /* Image helpers */

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream is1 = getContentResolver().openInputStream(uri);
            if (is1 == null) return null;
            BitmapFactory.decodeStream(is1, null, options);
            is1.close();

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            InputStream is2 = getContentResolver().openInputStream(uri);
            if (is2 == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
            is2.close();

            // Fix rotation based on EXIF so photos are upright
            bitmap = applyExifRotation(uri, bitmap);

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap applyExifRotation(Uri uri, Bitmap bitmap) {
        if (bitmap == null) return null;

        try {
            InputStream exifStream = getContentResolver().openInputStream(uri);
            if (exifStream == null) return bitmap;

            ExifInterface exif = new ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            exifStream.close();

            Matrix matrix = new Matrix();
            boolean rotate = false;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    rotate = true;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    rotate = true;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    rotate = true;
                    break;
                default:
                    break;
            }

            if (rotate) {
                Bitmap corrected = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );
                if (corrected != bitmap) {
                    bitmap.recycle();
                }
                return corrected;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap processBitmapForAvatar(Bitmap original) {
        if (original == null) return null;

        int width = original.getWidth();
        int height = original.getHeight();
        int size = Math.min(width, height);

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        Bitmap square = Bitmap.createBitmap(original, x, y, size, size);

        if (size > MAX_IMAGE_SIZE) {
            float scale = (float) MAX_IMAGE_SIZE / size;
            int newSize = Math.round(size * scale);
            square = Bitmap.createScaledBitmap(square, newSize, newSize, true);
        }

        return square;
    }

    private String saveImageToInternalStorage(Bitmap bitmap) {
        if (bitmap == null) return null;
        File directory = new File(getFilesDir(), "family_pics");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = "family_" + System.currentTimeMillis() + ".jpg";
        File outFile = new File(directory, fileName);

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            outputStream.flush();
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void startAvatarPulseAnimation() {
        if (photoContainer == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(photoContainer, "scaleX", 1f, 1.06f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(photoContainer, "scaleY", 1f, 1.06f);
        scaleX.setDuration(1200);
        scaleY.setDuration(1200);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    /* Adapter callbacks */

    @Override
    public void onEditClicked(FamilyMember member) {
        editingFamilyId = member.getFamilyId();
        editingImagePath = member.getImagePath();

        edtName.setText(member.getFirstName());
        edtRelationship.setText(member.getRelationship());
        btnSave.setText("Update");

        currentBitmap = null;

        String path = member.getImagePath();
        if (!TextUtils.isEmpty(path)) {
            File file = new File(path);
            Glide.with(this)
                    .load(file)
                    .centerCrop()
                    .into(imgFamilyPhoto);
        } else {
            imgFamilyPhoto.setImageResource(R.drawable.ic_child_avatar_placeholder);
        }
    }

    @Override
    public void onDeleteClicked(FamilyMember member) {
        new AlertDialog.Builder(this)
                .setTitle("Delete family member")
                .setMessage("Are you sure you want to delete " + member.getFirstName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean ok = deleteFamilyMember(member.getFamilyId());
                    if (ok) {
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        clearForm();
                        loadFamilyMembers();
                        updateCounter();
                        updateSaveEnabledState();
                    } else {
                        Toast.makeText(this, "Error deleting", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
