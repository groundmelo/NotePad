/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import static com.example.android.notepad.R.*;

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

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_TODO_STATUS,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR
    };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitleText;
    private EditText mText;
    private ImageView mTodoIcon;
    private TextView mCategoryText;
    private String mOriginalContent;
    private int mTodoStatus; // 当前待办状态
    private String mCategory; // 当前分类
    private String mCategoryColor; // 当前分类颜色

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 应用主题
        ThemeManager.applyTheme(this);
        // 启用ActionBar返回箭头
        getActionBar().setDisplayHomeAsUpEnabled(true);

        /*
         * Creates an Intent to use when the Activity object's result is sent back to the
         * caller.
         */
        final Intent intent = getIntent();

        /*
         *  Sets up for the edit, based on the action specified for the incoming Intent.
         */

        // Gets the action that triggered the intent filter for this Activity
        final String action = intent.getAction();

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // Check if the query returned a valid Cursor
        if (mCursor == null) {
            // Logs an error that the query failed
            Log.e(TAG, "Failed to query note for URI: " + mUri);
            // Closes the activity with a canceled result
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }

        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // Gets handles to the UI components in the layout.
        mTitleText = (EditText) findViewById(R.id.title);
        mText = (EditText) findViewById(R.id.note);
        mTodoIcon = (ImageView) findViewById(R.id.todo_icon);
        mCategoryText = (TextView) findViewById(R.id.category_text);
        
        // 应用字体大小
        ThemeManager.applyFontSize(mTitleText, this);
        ThemeManager.applyFontSize(mText, this);
        ThemeManager.applyFontSize(mCategoryText, this);
        
        // 设置待办图标点击事件
        mTodoIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTodoStatus();
            }
        });
        
        // 设置分类文本点击事件
        mCategoryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategorySelection();
            }
        });

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * The Activity is no longer paused. In response, this method does three things:
         *
         * 1) Re-queries the provider for the note associated with this Activity.
         * 2) Updates the title bar with the activity title.
         * 3) Sets the note text and restores the cursor position.
         *
         * Because this is the View phase of the lifecycle, the Activity's window is visible
         * but doesn't have focus.
         */

        // 检查mCursor是否有效
        if (mCursor == null || mCursor.isClosed()) {
            // 如果mCursor无效，重新查询
            mCursor = managedQuery(
                mUri,         // The URI that gets multiple notes from the provider.
                PROJECTION,   // A projection that returns the note ID and note content for each note.
                null,         // No "where" clause selection criteria.
                null,         // No "where" clause selection values.
                null          // Use the default sort order (modification date, descending)
            );
            
            // 如果重新查询后仍然无效，显示错误信息
            if (mCursor == null || mCursor.isClosed()) {
                setTitle(getText(R.string.error_title));
                mText.setText(getText(R.string.error_message));
                return;
            }
        } else {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();
        }

        /* Moves to the first record. Always call moveToFirst() before accessing data in
         * a Cursor for the first time. The semantics of using a Cursor are that when it is
         * created, its internal index is pointing to a "place" immediately before the first
         * record.
         */
        if (!mCursor.moveToFirst()) {
            // 如果Cursor为空，显示错误信息
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
            return;
        }

        // Modifies the window title for the Activity according to the current Activity state.
        if (mState == STATE_EDIT) {
            // Set the title of the Activity to include the note title
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String title = mCursor.getString(colTitleIndex);
            Resources res = getResources();
            String text = String.format(res.getString(R.string.title_edit), title);
            setTitle(text);
        // Sets the title to "create" for inserts
        } else if (mState == STATE_INSERT) {
            setTitle(getText(R.string.title_create));
        }

        /*
         * onResume() may have been called after the Activity lost focus (was paused).
         * The user was either editing or creating a note when the Activity paused.
         * The Activity should re-display the text that had been retrieved previously, but
         * it should not move the cursor. This helps the user to continue editing or entering.
         */

        // Gets the note title from the Cursor and puts it in the TextView, but doesn't change
        // the text cursor's position.
        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        String title = mCursor.getString(colTitleIndex);
        // 防止空指针异常，当title为null时使用空字符串
        mTitleText.setTextKeepState(title != null ? title : "");

        // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
        // the text cursor's position.
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String note = mCursor.getString(colNoteIndex);
        // 防止空指针异常，当note为null时使用空字符串
        mText.setTextKeepState(note != null ? note : "");

        // Stores the original note text, to allow the user to revert changes.
        if (mOriginalContent == null) {
            mOriginalContent = note;
        }
        
        // 加载待办状态
        int colTodoStatusIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TODO_STATUS);
        if (colTodoStatusIndex != -1) {
            mTodoStatus = mCursor.getInt(colTodoStatusIndex);
        } else {
            mTodoStatus = NotePad.Notes.TODO_STATUS_NONE;
        }
        
        // 加载分类信息
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
        
        // 更新待办图标和分类显示
        updateTodoIcon();
        updateCategoryDisplay();
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     *
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * This method is called when the Activity loses focus.
     *
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     *
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * Tests to see that the query operation didn't fail (see onCreate()). The Cursor object
         * will exist, even if no records were returned, unless the query failed because of some
         * exception or error.
         *
         */
        if (mCursor != null) {

            // Get the current note text.
            String text = mText.getText().toString();
            int length = text.length();

            /*
             * If the Activity is in the midst of finishing and there is no text in the current
             * note, returns a result of CANCELED to the caller, and deletes the note. This is done
             * even if the note was being edited, the assumption being that the user wanted to
             * "clear out" (delete) the note.
             */
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * Writes the edits to the provider. The note has been edited if an existing note was
                 * retrieved into the editor *or* if a new note was inserted. In the latter case,
                 * onCreate() inserted a new empty note into the provider, and it is this new note
                 * that is being edited.
                 */
            } else if (mState == STATE_EDIT) {
                // Creates a map to contain the new values for the columns
                String title = mTitleText.getText().toString();
                updateNote(text, title);
            } else if (mState == STATE_INSERT) {
                String title = mTitleText.getText().toString();
                updateNote(text, title);
                mState = STATE_EDIT;
          }
        }
    }

    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Builds the menus for editing and inserting, and adds in alternative actions that
     * registered themselves to handle the MIME types for this application.
     *
     * @param menu A Menu object to which items should be added.
     * @return True to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 添加待办相关的菜单项
        MenuItem todoItem = menu.add(Menu.NONE, R.id.menu_todo, Menu.NONE, R.string.menu_todo);
        todoItem.setIcon(R.drawable.ic_todo_pending);
        todoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        
        // 根据当前待办状态设置菜单项文本
        updateTodoMenuItem(todoItem);
        


        // Only add extra menu items for a saved note 
        if (mState == STATE_EDIT) {
            // Append to the
            // menu items for any other activities that can do stuff with it
            // as well.  This does a query on the system for any activities that
            // implement the ALTERNATIVE_ACTION for our data, adding a menu item
            // for each one that is found.
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Check if note has changed and enable/disable the revert option
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        // 防止空指针异常，当savedNote为null时特殊处理
        if ((savedNote == null && currentNote == null) || (savedNote != null && savedNote.equals(currentNote))) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This method is called when a menu item is selected. Android passes in the selected item.
     * The switch statement in this method calls the appropriate method to perform the action the
     * user chose.
     *
     * @param item The selected MenuItem
     * @return True to indicate that the item was processed, and no further work is necessary. False
     * to proceed to further processing as indicated in the MenuItem object.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        int id = item.getItemId();
        // 处理返回箭头
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
            // 切换待办状态
            toggleTodoStatus();
            updateTodoMenuItem(item);
        } else if (id == R.id.menu_font_size_small) {
            // 设置小字体
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_SMALL);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_medium) {
            // 设置中字体
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_MEDIUM);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_large) {
            // 设置大字体
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_LARGE);
            applyFontSize();
            return true;
        } else if (id == R.id.menu_font_size_xlarge) {
            // 设置超大字体
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_XLARGE);
            applyFontSize();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 应用字体大小到所有相关UI组件
     */
    private void applyFontSize() {
        ThemeManager.applyFontSize(mTitleText, this);
        ThemeManager.applyFontSize(mText, this);
        ThemeManager.applyFontSize(mCategoryText, this);
    }
    
    /**
     * 切换待办状态
     */
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
        // 更新待办图标
        updateTodoIcon();
        // 立即保存待办状态
        saveTodoStatus();
    }
    
    /**
     * 更新待办状态图标
     */
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
    
    /**
     * 显示分类选择对话框
     */
    private void showCategorySelection() {
        // 从ContentProvider获取所有分类
        Cursor categoryCursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                null,
                null,
                NotePad.Categories.COLUMN_NAME_NAME + " ASC"
        );
        
        // 创建分类列表
        final ArrayList<String> categories = new ArrayList<>();
        final ArrayList<String> categoryColors = new ArrayList<>();
        
        // 添加默认分类
        categories.add(getString(R.string.default_category));
        categoryColors.add("#808080");
        
        // 遍历分类Cursor，添加所有分类
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
        
        // 转换为数组
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
    
    /**
     * 保存待办状态到数据库
     */
    private void saveTodoStatus() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, mTodoStatus);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used.
                null     // No where arguments are necessary.
        );
    }
    
    /**
     * 更新待办菜单项的显示
     */
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

//BEGIN_INCLUDE(paste)
    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {

        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // Gets the first item from the clipboard data
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI pointing to a note
            Uri uri = item.getUri();

            // Tests to see that the item actually is an URI, and that the URI
            // is a content URI pointing to a provider whose MIME type is the same
            // as the MIME type supported by the Note pad provider.
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // 定义需要查询的字段
                String[] projection = {NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE};
                
                // The clipboard holds a reference to data with a note MIME type. This copies it.
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        projection,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                // If the Cursor is not null, and it contains at least one record
                // (moveToFirst() returns true), then this gets the note data from it.
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            // If the contents of the clipboard wasn't a reference to a note, then
            // this converts whatever it is to text.
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the current note with the retrieved title and text.
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param title The new note title to use
     * @param text The new note contents to use.
     */
    private final void updateNote(String text, String title) {

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        
        // 保存待办状态
        values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, mTodoStatus);
        
        // 保存分类信息
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR, mCategoryColor);

        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );


    }

    /**
     * This helper method cancels the work done on a note.  It deletes the note if it was
     * newly created, or reverts to the original text of the note i
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
    
    /**
     * 更新分类显示
     */
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
        // Rebuild our UI with the new configuration
        updateUI();
    }
    
    /**
     * 更新UI以适应配置变化
     */
    private void updateUI() {
        // 重新设置标题
        if (mState == STATE_EDIT && mCursor != null && !mCursor.isClosed() && mCursor.moveToFirst()) {
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String title = mCursor.getString(colTitleIndex);
            Resources res = getResources();
            String text = String.format(res.getString(R.string.title_edit), title != null ? title : "");
            setTitle(text);
        } else if (mState == STATE_INSERT) {
            setTitle(getText(R.string.title_create));
        }
        // 更新待办图标和分类显示
        updateTodoIcon();
        updateCategoryDisplay();
    }
}
