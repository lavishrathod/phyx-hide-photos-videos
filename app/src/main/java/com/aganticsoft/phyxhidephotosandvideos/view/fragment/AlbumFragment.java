package com.aganticsoft.phyxhidephotosandvideos.view.fragment;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.aganticsoft.phyxhidephotosandvideos.Constants;
import com.aganticsoft.phyxhidephotosandvideos.PhyxApp;
import com.aganticsoft.phyxhidephotosandvideos.R;
import com.aganticsoft.phyxhidephotosandvideos.di.Injectable;
import com.aganticsoft.phyxhidephotosandvideos.model.MediaModel;
import com.aganticsoft.phyxhidephotosandvideos.util.CryptoUtils;
import com.aganticsoft.phyxhidephotosandvideos.util.FilePathUtils;
import com.aganticsoft.phyxhidephotosandvideos.util.FormatUtils;
import com.aganticsoft.phyxhidephotosandvideos.util.PrefManager;
import com.aganticsoft.phyxhidephotosandvideos.view.activity.MediaChooseActivity;
import com.bumptech.glide.Glide;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

/**
 * Created by ttson
 * Date: 9/22/2017.
 */

public class AlbumFragment extends BaseFragment implements Injectable {


    @BindView(R.id.fabAddFile)
    FloatingActionButton fabAddFile;
    @BindView(R.id.fabAddPhoto)
    FloatingActionButton fabAddPhoto;
    @BindView(R.id.fabAddVideo)
    FloatingActionButton fabAddVideo;
    @BindView(R.id.fabAddAlbum)
    FloatingActionButton fabAddAlbum;
    @BindView(R.id.multiple_actions)
    FloatingActionsMenu fabMenus;

    @BindView(R.id.rvAlbum)
    RecyclerView rvAlbum;

    public static final int REQUEST_CHOOSE_IMAGES = 0x91;
    public static final int REQUEST_CHOOSE_VIDEOS = 0x92;
    public static final int REQUEST_CHOOSE_FILES = 0x93;


    @Inject
    PrefManager prefManager;
    private Context mContext;

    public static AlbumFragment newInstance() {
        AlbumFragment frg = new AlbumFragment();
        Bundle bundle = new Bundle();

        frg.setArguments(bundle);

        return frg;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Context context)  {
        super.onAttach(context);
        this.mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_album, container, false);
        ButterKnife.bind(this , v);



        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CHOOSE_FILES:
                    ClipData clipData = data.getClipData();
                    Uri uri = data.getData();

                    if (uri != null) { // single file pick
                        saveToStorage(uri);
                    }
                    else if (clipData != null && clipData.getItemCount() > 0 ) { // multiple file click
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            ClipData.Item item = clipData.getItemAt(i);

                            saveToStorage(item.getUri());
                        }
                    }

                    break;
                case REQUEST_CHOOSE_IMAGES:
                    List<MediaModel> imagesModel = data.getParcelableArrayListExtra("data");

                    for (MediaModel ml : imagesModel) {
                        saveToStorage(new File(ml.bucketUrl()));
                    }

                    break;
                case REQUEST_CHOOSE_VIDEOS:
                    List<MediaModel> videoModels = data.getParcelableArrayListExtra("data");

                    for (MediaModel ml : videoModels) {
                        saveToStorage(new File(ml.bucketUrl()));
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
    }

    // <editor-fold desc="[ =============== STORAGE FUNCTION  ===================]">
    private void saveToStorage(Uri uri)
    {

        if (uri.toString().startsWith("content://")) {
            Cursor cursor = null;
            try {
                cursor = getActivity().getContentResolver().query(uri
                        , new String[] {
                                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
                        }, null, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    InputStream in =  getActivity().getContentResolver().openInputStream(uri);
                    int numBytes = in.available();
                    byte[] buffer = new byte[numBytes];
                    in.read(buffer);

                    CryptoUtils.saveFile(PhyxApp.getInstance().getStorageManager(), buffer, Constants.PATH_MAINALBUM
                            + displayName);

                    String realPath = FilePathUtils.getPath(mContext, uri);
                    Uri uriPath = Uri.fromFile(new File(Constants.PATH_MAINALBUM
                            + displayName));

                    File originalFile = new File(realPath);
                    originalFile.delete();


                    Timber.e("content:// sendBroadcast urirealPath: %s, realPath: %s, originUri: %s",
                            uriPath.toString(), realPath, uri.toString());
                    scanFile(Constants.PATH_MAINALBUM + displayName, mContext); // TODO: currently not work
                    delete(mContext, originalFile.getPath());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (cursor != null)
                    cursor.close();
            }
        } else if (uri.toString().startsWith("file://")) {
            File originalFile = new File(uri.getPath());

            saveToStorage(originalFile);
        }
    }

    private void saveToStorage(File originalFile) {
        int size = (int) originalFile.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(originalFile));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String newPath = Constants.PATH_MAINALBUM + originalFile.getName();

        CryptoUtils.saveFile(PhyxApp.getInstance().getStorageManager()
                , bytes, newPath);

        originalFile.delete();

        Timber.e("addFile originalFilePath: %s, newpath: %s", originalFile.getAbsolutePath(), newPath);


        scanFile(newPath, mContext);
        delete(mContext, originalFile.getPath());
    }

    public static void scanFile(String path, Context c) {
        System.out.println(path + " " + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 19) {
            MediaScannerConnection.scanFile(c, new String[]{path}, null, (path1, uri) -> {

            });
        } else {
            Uri contentUri = Uri.fromFile(new File(path));
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
            c.sendBroadcast(mediaScanIntent);
        }
    }

    private void delete(final Context context, final String file) {
        final String where = MediaStore.MediaColumns.DATA + "=?";
        final String[] selectionArgs = new String[] {file};
        final ContentResolver contentResolver = context.getContentResolver();
        final Uri filesUri = MediaStore.Files.getContentUri("external");
        // Delete the entry from the media database. This will actually delete media files.
        contentResolver.delete(filesUri, where, selectionArgs);

    }

    // </editor-fold>


    // <editor-fold desc="[ =============== BIND CLICK ===================]">

    @OnClick(R.id.fabAddPhoto)
    public void onClickAddPhoto() {
        startActivityForResult(MediaChooseActivity.getIntent(mContext
                , MediaModel.MediaType.TYPE_IMAGE), REQUEST_CHOOSE_IMAGES);

        fabMenus.collapse();
    }

    @OnClick(R.id.fabAddVideo)
    public void onClickAddVideo() {
        startActivityForResult(MediaChooseActivity.getIntent(mContext
                , MediaModel.MediaType.TYPE_VIDEO), REQUEST_CHOOSE_VIDEOS);
        fabMenus.collapse();
    }

    @OnClick(R.id.fabAddAlbum)
    public void onClickAddAlbum() {
        fabMenus.collapse();

        final EditText etName = new EditText(mContext);
        etName.setSingleLine();
        FrameLayout containerView = new FrameLayout(mContext);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_add_album_margin);
        params.rightMargin = params.leftMargin;
        params.topMargin = params.leftMargin;
        params.bottomMargin = params.bottomMargin;

        etName.setLayoutParams(params);
        containerView.addView(etName);

        etName.setHint("Album name");

        AlertDialog alg = new AlertDialog.Builder(mContext)
                .setTitle("Create new album:")
                .setNegativeButton("CREATE", ((dialogInterface, i) -> {
                    dialogInterface.dismiss();
                }))
                .setPositiveButton("CANCEL", (dialogInterface, i) -> {
                    File f = new File(Constants.PATH_APP_DIR + etName.getText().toString());

                    if (!f.exists())
                        f.mkdirs();

                    dialogInterface.dismiss();
                })
                .setView(containerView)
                .create();

        alg.show();

        etName.post(() -> {
            Timber.e("etname: %s", etName.getLayoutParams().getClass().getName());
        });
    }

    @OnClick(R.id.fabAddFile)
    public void onClickAddFile() {
        fabMenus.collapse();

        Intent intent = new Intent();
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select Files"), REQUEST_CHOOSE_FILES);

        Toast.makeText(mContext, "Long hold item to multi select !", Toast.LENGTH_SHORT).show();
    }

    // </editor-fold>
}
