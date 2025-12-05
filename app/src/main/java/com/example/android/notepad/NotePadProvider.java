package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    private static final String TAG = "NotePadProvider";

    private static final String DATABASE_NAME = "note_pad.db";

    private static final int DATABASE_VERSION = 4;

    private static HashMap<String, String> sNotesProjectionMap;

    private static HashMap<String, String> sLiveFolderProjectionMap;

    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_TITLE,
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    private static final int NOTES = 1;

    private static final int NOTE_ID = 2;

    private static final int LIVE_FOLDER_NOTES = 3;

    private static final int CATEGORIES = 4;

    private static final int CATEGORY_ID = 5;

    private static final int NOTES_BY_CATEGORY = 6;

    private static final UriMatcher sUriMatcher;

    private DatabaseHelper mOpenHelper;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

        sUriMatcher.addURI(NotePad.AUTHORITY, "categories", CATEGORIES);

        sUriMatcher.addURI(NotePad.AUTHORITY, "categories/#", CATEGORY_ID);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/category/#", NOTES_BY_CATEGORY);

        sNotesProjectionMap = new HashMap<String, String>();

        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_TODO_STATUS,
                NotePad.Notes.COLUMN_NAME_TODO_STATUS);

        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.COLUMN_NAME_CATEGORY);

        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR,
                NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR);

        sLiveFolderProjectionMap = new HashMap<String, String>();

        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
                LiveFolders.NAME);
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_TODO_STATUS + " INTEGER DEFAULT 0,"
                    + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '其他',"
                    + NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR + " TEXT DEFAULT '#9E9E9E'"
                    + ");");

            db.execSQL("CREATE TABLE " + NotePad.Categories.TABLE_NAME + " ("
                    + NotePad.Categories._ID + " INTEGER PRIMARY KEY,"
                    + NotePad.Categories.COLUMN_NAME_NAME + " TEXT UNIQUE,"
                    + NotePad.Categories.COLUMN_NAME_COLOR + " TEXT"
                    + ");");

            ContentValues values = new ContentValues();
            values.put(NotePad.Categories.COLUMN_NAME_NAME, "工作");
            values.put(NotePad.Categories.COLUMN_NAME_COLOR, "#FF5722");
            db.insert(NotePad.Categories.TABLE_NAME, null, values);

            values.clear();
            values.put(NotePad.Categories.COLUMN_NAME_NAME, "学习");
            values.put(NotePad.Categories.COLUMN_NAME_COLOR, "#4CAF50");
            db.insert(NotePad.Categories.TABLE_NAME, null, values);

            values.clear();
            values.put(NotePad.Categories.COLUMN_NAME_NAME, "生活");
            values.put(NotePad.Categories.COLUMN_NAME_COLOR, "#2196F3");
            db.insert(NotePad.Categories.TABLE_NAME, null, values);

            values.clear();
            values.put(NotePad.Categories.COLUMN_NAME_NAME, "其他");
            values.put(NotePad.Categories.COLUMN_NAME_COLOR, "#9E9E9E");
            db.insert(NotePad.Categories.TABLE_NAME, null, values);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            db.execSQL("DROP TABLE IF EXISTS notes");
            db.execSQL("DROP TABLE IF EXISTS " + NotePad.Categories.TABLE_NAME);

            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                qb.setTables(NotePad.Notes.TABLE_NAME);
                qb.setProjectionMap(sNotesProjectionMap);
                break;

            case NOTE_ID:
                qb.setTables(NotePad.Notes.TABLE_NAME);
                qb.setProjectionMap(sNotesProjectionMap);
                qb.appendWhere(
                        NotePad.Notes._ID +
                                "=" +
                                uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
                break;

            case LIVE_FOLDER_NOTES:
                qb.setTables(NotePad.Notes.TABLE_NAME);
                qb.setProjectionMap(sLiveFolderProjectionMap);
                break;

            case CATEGORIES:
                qb.setTables(NotePad.Categories.TABLE_NAME);
                break;

            case CATEGORY_ID:
                qb.setTables(NotePad.Categories.TABLE_NAME);
                qb.appendWhere(
                        NotePad.Categories._ID +
                                "=" +
                                uri.getPathSegments().get(1));
                break;

            case NOTES_BY_CATEGORY:
                qb.setTables(NotePad.Notes.TABLE_NAME + " JOIN " + NotePad.Categories.TABLE_NAME + " ON " + NotePad.Notes.COLUMN_NAME_CATEGORY + " = " + NotePad.Categories.COLUMN_NAME_NAME);
                qb.setProjectionMap(sNotesProjectionMap);
                qb.appendWhere(
                        NotePad.Categories._ID +
                                "=" +
                                uri.getPathSegments().get(2));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            if (sUriMatcher.match(uri) == CATEGORIES || sUriMatcher.match(uri) == CATEGORY_ID) {
                orderBy = NotePad.Categories.DEFAULT_SORT_ORDER;
            } else {
                orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
            }
        } else {
            orderBy = sortOrder;
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        Cursor c = qb.query(
                db,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        );

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.CONTENT_TYPE;

            case CATEGORIES:
                return NotePad.Categories.CONTENT_TYPE;

            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;

            case CATEGORY_ID:
                return NotePad.Categories.CONTENT_ITEM_TYPE;

            case NOTES_BY_CATEGORY:
                return NotePad.Notes.CONTENT_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        if (mimeTypes != null) {
            Cursor c = query(
                    uri,
                    READ_NOTE_PROJECTION,
                    null,
                    null,
                    null
            );

            if (c == null || !c.moveToFirst()) {
                if (c != null) {
                    c.close();
                }
                throw new FileNotFoundException("Unable to query " + uri);
            }

            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                                Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        ContentValues values;
        String table;
        Uri contentUri;

        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                table = NotePad.Notes.TABLE_NAME;
                contentUri = NotePad.Notes.CONTENT_ID_URI_BASE;

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
                    values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
                }

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
                    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
                }

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
                    Resources r = Resources.getSystem();
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
                }

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE)) {
                    values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
                }

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TODO_STATUS)) {
                    values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, 0);
                }

                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY)) {
                    values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, "其他");
                }
                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR)) {
                    values.put(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR, "#9E9E9E");
                }
                break;

            case CATEGORIES:
                table = NotePad.Categories.TABLE_NAME;
                contentUri = NotePad.Categories.CONTENT_ID_URI_BASE;

                if (!values.containsKey(NotePad.Categories.COLUMN_NAME_NAME)) {
                    throw new SQLException("Category name is required");
                }

                if (!values.containsKey(NotePad.Categories.COLUMN_NAME_COLOR)) {
                    values.put(NotePad.Categories.COLUMN_NAME_COLOR, "#9E9E9E");
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowId = db.insert(
                table,
                null,
                values
        );

        if (rowId > 0) {
            Uri insertedUri = ContentUris.withAppendedId(contentUri, rowId);
            getContext().getContentResolver().notifyChange(insertedUri, null);
            return insertedUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;
        int count;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(
                        NotePad.Notes.TABLE_NAME,
                        where,
                        whereArgs
                );
                break;

            case NOTE_ID:
                finalWhere =
                        NotePad.Notes._ID +
                                " = " +
                                uri.getPathSegments().
                                        get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                count = db.delete(
                        NotePad.Notes.TABLE_NAME,
                        finalWhere,
                        whereArgs
                );
                break;

            case CATEGORIES:
                count = db.delete(
                        NotePad.Categories.TABLE_NAME,
                        where,
                        whereArgs
                );
                break;

            case CATEGORY_ID:
                finalWhere =
                        NotePad.Categories._ID +
                                " = " +
                                uri.getPathSegments().get(1)
                ;

                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                Cursor cursor = db.query(
                        NotePad.Categories.TABLE_NAME,
                        new String[]{NotePad.Categories.COLUMN_NAME_NAME},
                        finalWhere,
                        whereArgs,
                        null,
                        null,
                        null
                );

                String categoryName = null;
                if (cursor != null && cursor.moveToFirst()) {
                    categoryName = cursor.getString(0);
                    cursor.close();
                }

                count = db.delete(
                        NotePad.Categories.TABLE_NAME,
                        finalWhere,
                        whereArgs
                );

                if (count > 0 && categoryName != null) {
                    ContentValues updateValues = new ContentValues();
                    updateValues.put(NotePad.Notes.COLUMN_NAME_CATEGORY, "其他");
                    updateValues.put(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR, "#9E9E9E");

                    db.update(
                            NotePad.Notes.TABLE_NAME,
                            updateValues,
                            NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?",
                            new String[]{categoryName}
                    );

                    getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
                    Long now = Long.valueOf(System.currentTimeMillis());
                    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
                }
                count = db.update(
                        NotePad.Notes.TABLE_NAME,
                        values,
                        where,
                        whereArgs
                );
                break;

            case NOTE_ID:
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
                    Long now = Long.valueOf(System.currentTimeMillis());
                    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
                }
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                finalWhere =
                        NotePad.Notes._ID +
                                " = " +
                                uri.getPathSegments().
                                        get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                count = db.update(
                        NotePad.Notes.TABLE_NAME,
                        values,
                        finalWhere,
                        whereArgs
                );
                break;

            case CATEGORIES:
                count = db.update(
                        NotePad.Categories.TABLE_NAME,
                        values,
                        where,
                        whereArgs
                );
                break;

            case CATEGORY_ID:
                String categoryId = uri.getPathSegments().get(1);

                count = db.update(
                        NotePad.Categories.TABLE_NAME,
                        values,
                        NotePad.Categories._ID + " = " + categoryId,
                        whereArgs
                );

                if (count > 0) {
                    Cursor cursor = db.query(
                            NotePad.Categories.TABLE_NAME,
                            new String[]{NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                            NotePad.Categories._ID + " = " + categoryId,
                            null,
                            null,
                            null,
                            null
                    );

                    if (cursor != null && cursor.moveToFirst()) {
                        String updatedName = cursor.getString(0);
                        String updatedColor = cursor.getString(1);
                        cursor.close();

                        ContentValues noteValues = new ContentValues();
                        noteValues.put(NotePad.Notes.COLUMN_NAME_CATEGORY, updatedName);
                        noteValues.put(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR, updatedColor);

                        db.update(
                                NotePad.Notes.TABLE_NAME,
                                noteValues,
                                NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?",
                                new String[]{updatedName}
                        );
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}