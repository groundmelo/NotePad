package com.example.android.notepad;

import java.util.ArrayList;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.content.res.Configuration;
public class NoteEditor extends Activity {
    private static final String TAG = "NoteEditor";

    private static final String[] PROJECTION =
            new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_TODO_STATUS,
                    NotePad.Notes.COLUMN_NAME_CATEGORY,
                    NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR
            };

    private static final String ORIGINAL_CONTENT = "origContent";

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitleText;
    private EditText mText;
    private ImageView mTodoIcon;
    private TextView mCategoryText;
    private String mOriginalContent;
    private int mTodoStatus;
    private String mCategory;
    private String mCategoryColor;

    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            int count = getLineCount();

            Rect r = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < count; i++) {

                int baseline = getLineBounds(i, r);

                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        final Intent intent = getIntent();

        final String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {

            mState = STATE_EDIT;
            mUri = intent.getData();

        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            if (mUri == null) {

                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                finish();
                return;
            }

            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {

            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        mCursor = managedQuery(
                mUri,
                PROJECTION,
                null,
                null,
                null
        );

        if (mCursor == null) {
            Log.e(TAG, "Failed to query note for URI: " + mUri);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        if (Intent.ACTION_PASTE.equals(action)) {
            performPaste();
            mState = STATE_EDIT;
        }

        setContentView(R.layout.note_editor);

        mTitleText = (EditText) findViewById(R.id.title);
        mText = (EditText) findViewById(R.id.note);
        mTodoIcon = (ImageView) findViewById(R.id.todo_icon);
        mCategoryText = (TextView) findViewById(R.id.category_text);

        ThemeManager.applyFontSize(mTitleText, this);
        ThemeManager.applyFontSize(mText, this);
        ThemeManager.applyFontSize(mCategoryText, this);

        mTodoIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTodoStatus();
            }
        });

        mCategoryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategorySelection();
            }
        });

        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor == null || mCursor.isClosed()) {
            mCursor = managedQuery(
                    mUri,
                    PROJECTION,
                    null,
                    null,
                    null
            );

            if (mCursor == null || mCursor.isClosed()) {
                setTitle(getText(R.string.error_title));
                mText.setText(getText(R.string.error_message));
                return;
            }
        } else {
            mCursor.requery();
        }

        if (!mCursor.moveToFirst()) {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
            return;
        }

        if (mState == STATE_EDIT) {
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String title = mCursor.getString(colTitleIndex);
            Resources res = getResources();
            String text = String.format(res.getString(R.string.title_edit), title);
            setTitle(text);
        } else if (mState == STATE_INSERT) {
            setTitle(getText(R.string.title_create));
        }

        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        String title = mCursor.getString(colTitleIndex);
        mTitleText.setTextKeepState(title != null ? title : "");

        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String note = mCursor.getString(colNoteIndex);
        mText.setTextKeepState(note != null ? note : "");

        if (mOriginalContent == null) {
            mOriginalContent = note;
        }

        int colTodoStatusIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TODO_STATUS);
        if (colTodoStatusIndex != -1) {
            mTodoStatus = mCursor.getInt(colTodoStatusIndex);
        } else {
            mTodoStatus = NotePad.Notes.TODO_STATUS_NONE;
        }

        int colCategoryIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
        if (colCategoryIndex != -1) {
            mCategory = mCursor.getString(colCategoryIndex);
        } else {
            mCategory = getString(R.string.default_category);
        }

        int colCategoryColorIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR);
        if (colCategoryColorIndex != -1) {
            mCategoryColor = mCursor.getString(colCategoryColorIndex);
        } else {
            mCategoryColor = "#808080";
        }

        updateTodoIcon();
        updateCategoryDisplay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {

            String text = mText.getText().toString();
            int length = text.length();

            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

            } else if (mState == STATE_EDIT) {
                String title = mTitleText.getText().toString();
                updateNote(text, title);
            } else if (mState == STATE_INSERT) {
                String title = mTitleText.getText().toString();
                updateNote(text, title);
                mState = STATE_EDIT;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        MenuItem todoItem = menu.add(Menu.NONE, R.id.menu_todo, Menu.NONE, R.string.menu_todo);
        todoItem.setIcon(R.drawable.ic_todo_pending);
        todoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        updateTodoMenuItem(todoItem);



        if (mState == STATE_EDIT) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if ((savedNote == null && currentNote == null) || (savedNote != null && savedNote.equals(currentNote))) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if(id== R.id.menu_save) {
            String title = mTitleText.getText().toString();
            String text = mText.getText().toString();
            updateNote(text, title);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
            finish();
        } else if (id == R.id.menu_todo) {
            toggleTodoStatus();
            updateTodoMenuItem(item);
        } else if (id == R.id.menu_font_size_small) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_SMALL);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_medium) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_MEDIUM);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_large) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_LARGE);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_xlarge) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_XLARGE);
            applyFontSize();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyFontSize() {
        ThemeManager.applyFontSize(mTitleText, this);
        ThemeManager.applyFontSize(mText, this);
        ThemeManager.applyFontSize(mCategoryText, this);
    }

    private void toggleTodoStatus() {
        switch (mTodoStatus) {
            case NotePad.Notes.TODO_STATUS_NONE:
                mTodoStatus = NotePad.Notes.TODO_STATUS_PENDING;
                break;
            case NotePad.Notes.TODO_STATUS_PENDING:
                mTodoStatus = NotePad.Notes.TODO_STATUS_COMPLETED;
                break;
            case NotePad.Notes.TODO_STATUS_COMPLETED:
                mTodoStatus = NotePad.Notes.TODO_STATUS_NONE;
                break;
        }
        updateTodoIcon();
        saveTodoStatus();
    }

    private void updateTodoIcon() {
        if (mTodoIcon != null) {
            if (mTodoStatus == NotePad.Notes.TODO_STATUS_PENDING) {
                mTodoIcon.setImageResource(R.drawable.ic_todo_pending);
            } else if (mTodoStatus == NotePad.Notes.TODO_STATUS_COMPLETED) {
                mTodoIcon.setImageResource(R.drawable.ic_todo_completed);
            } else {
                mTodoIcon.setImageResource(R.drawable.ic_todo_unchecked);
            }
        }
    }

    private void showCategorySelection() {
        Cursor categoryCursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                null,
                null,
                NotePad.Categories.COLUMN_NAME_NAME + " ASC"
        );

        final ArrayList<String> categories = new ArrayList<>();
        final ArrayList<String> categoryColors = new ArrayList<>();

        categories.add(getString(R.string.default_category));
        categoryColors.add("#808080");

        if (categoryCursor != null && categoryCursor.moveToFirst()) {
            do {
                String categoryName = categoryCursor.getString(categoryCursor.getColumnIndex(NotePad.Categories.COLUMN_NAME_NAME));
                String categoryColor = categoryCursor.getString(categoryCursor.getColumnIndex(NotePad.Categories.COLUMN_NAME_COLOR));
                if (categoryName != null && !categoryName.equals(getString(R.string.default_category))) {
                    categories.add(categoryName);
                    categoryColors.add(categoryColor);
                }
            } while (categoryCursor.moveToNext());
            categoryCursor.close();
        } else if (categoryCursor != null) {
            categoryCursor.close();
        }

        final String[] categoryArray = categories.toArray(new String[categories.size()]);
        final String[] categoryColorArray = categoryColors.toArray(new String[categoryColors.size()]);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("选择分类");
        builder.setItems(categoryArray, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                mCategory = categoryArray[which];
                mCategoryColor = categoryColorArray[which];
                updateCategoryDisplay();
            }
        });
        builder.show();
    }

    private void saveTodoStatus() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, mTodoStatus);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        getContentResolver().update(
                mUri,
                values,
                null,
                null
        );
    }

    private void updateTodoMenuItem(MenuItem item) {
        switch (mTodoStatus) {
            case NotePad.Notes.TODO_STATUS_NONE:
                item.setTitle(R.string.menu_todo_mark_pending);
                item.setIcon(R.drawable.ic_todo_pending);
                break;
            case NotePad.Notes.TODO_STATUS_PENDING:
                item.setTitle(R.string.menu_todo_mark_completed);
                item.setIcon(R.drawable.ic_todo_completed);
                break;
            case NotePad.Notes.TODO_STATUS_COMPLETED:
                item.setTitle(R.string.menu_todo_remove);
                item.setIcon(R.drawable.ic_todo_pending);
                break;
        }
    }

    private final void performPaste() {

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        ContentResolver cr = getContentResolver();

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            ClipData.Item item = clip.getItemAt(0);

            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                String[] projection = {NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE};

                Cursor orig = cr.query(
                        uri,
                        projection,
                        null,
                        null,
                        null
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    orig.close();
                }
            }

            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            updateNote(text, title);
        }
    }

    private final void updateNote(String text, String title) {

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, mTodoStatus);

        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR, mCategoryColor);

        getContentResolver().update(
                mUri,
                values,
                null,
                null
        );


    }

    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }

    private void updateCategoryDisplay() {
        if (mCategoryText != null) {
            mCategoryText.setText(mCategory);
            try {
                mCategoryText.setBackgroundColor(android.graphics.Color.parseColor(mCategoryColor));
            } catch (IllegalArgumentException e) {
                mCategoryText.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"));
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
    }

    private void updateUI() {
        if (mState == STATE_EDIT && mCursor != null && !mCursor.isClosed() && mCursor.moveToFirst()) {
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String title = mCursor.getString(colTitleIndex);
            Resources res = getResources();
            String text = String.format(res.getString(R.string.title_edit), title != null ? title : "");
            setTitle(text);
        } else if (mState == STATE_INSERT) {
            setTitle(getText(R.string.title_create));
        }
        updateTodoIcon();
        updateCategoryDisplay();
    }
}
