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

import com.example.android.notepad.NotePad;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.view.MenuItem;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import android.view.LayoutInflater;
import android.graphics.Color;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    // 搜索条件
    private String mSearchFilter = null;

    // 请求码
    private static final int SEARCH_REQUEST = 1;

    // 基本投影数组，包含分类和内容字段
    private static final String[] PROJECTION = new String[] {
        NotePad.Notes._ID,
        NotePad.Notes.COLUMN_NAME_TITLE,
        NotePad.Notes.COLUMN_NAME_NOTE,
        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
        NotePad.Notes.COLUMN_NAME_TODO_STATUS,
        NotePad.Notes.COLUMN_NAME_CATEGORY,
        NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR
    };

    // 列索引
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_NOTE = 2;
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 3;
    private static final int COLUMN_INDEX_TODO_STATUS = 4;
    private static final int COLUMN_INDEX_CATEGORY = 5;
    // 分类颜色列索引
    private static final int COL_CATEGORY_COLOR_INDEX = 6;

    // 分类筛选相关变量
    private Spinner mCategorySpinner;
    private Button mClearFilterButton;
    private String mCurrentFilterCategory = null;
    
    // 排序相关变量
    private Spinner mSortSpinner;
    private String mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
    
    // 用于手动管理Cursor的生命周期
    private Cursor mCursor;
    
    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 应用主题
        ThemeManager.applyTheme(this);
        
        // 设置自定义布局文件
        setContentView(R.layout.noteslist);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);
        
        // 初始化分类筛选器
        initCategoryFilter();
        
        // 初始化排序控件
        initSort();

        // 执行查询并设置适配器
        refreshNoteList();
    }
    
    /**
     * 当Activity从后台返回前台时调用，确保Cursor是有效的
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 如果Cursor为null或已关闭，重新查询
        if (mCursor == null || mCursor.isClosed()) {
            refreshNoteList();
        }
    }
    
    /**
     * 当Activity销毁时调用，关闭Cursor释放资源
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭Cursor，释放资源
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);
        
        // 创建背景颜色切换子菜单
        SubMenu backgroundMenu = menu.addSubMenu("切换背景颜色");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_white, Menu.NONE, "白色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_blue, Menu.NONE, "蓝色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_yellow, Menu.NONE, "黄色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_pink, Menu.NONE, "粉色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_green, Menu.NONE, "绿色背景");
        


        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);



        // Gets the number of notes currently being displayed.
        final boolean haveItems = getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                Menu.NONE,                  // A unique item ID is not required.
                Menu.NONE,                  // The alternatives don't need to be in order.
                null,                       // The caller's name is not excluded from the group.
                specifics,                  // These specific options must appear first.
                intent,                     // These Intent objects map to the options in specifics.
                Menu.NONE,                  // No flags are required.
                items                       // The menu items generated from the specifics-to-
                                            // Intents mapping
            );
                // If the Edit menu item exists, adds shortcuts for it.
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // Displays the menu
        return true;
    }

    /**
     * 初始化分类筛选器
     */
    private void initCategoryFilter() {
        mCategorySpinner = (Spinner) findViewById(R.id.category_spinner);
        mClearFilterButton = (Button) findViewById(R.id.clear_filter_button);
        
        // 从分类表获取所有分类
        Cursor categoryCursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME},
                null,
                null,
                NotePad.Categories.COLUMN_NAME_NAME + " ASC"
        );
        
        // 创建分类列表，添加"全部"选项
        ArrayList<String> categories = new ArrayList<>();
        categories.add("全部");
        
        // 遍历分类Cursor，添加所有分类
        if (categoryCursor != null && categoryCursor.moveToFirst()) {
            do {
                String category = categoryCursor.getString(0);
                if (category != null) {
                    categories.add(category);
                }
            } while (categoryCursor.moveToNext());
            categoryCursor.close(); // 关闭临时Cursor，释放资源
        } else if (categoryCursor != null) {
            categoryCursor.close(); // 关闭临时Cursor，释放资源
        }
        
        // 设置分类适配器
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categories
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(categoryAdapter);
        
        // 设置分类选择监听器
        mCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // 选择了"全部"，清除筛选
                    mCurrentFilterCategory = null;
                } else {
                    // 设置当前筛选分类
                    mCurrentFilterCategory = (String) parent.getItemAtPosition(position);
                }
                // 刷新列表
                refreshNoteList();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 什么都不做
            }
        });
        
        // 设置清除筛选按钮监听器
        mClearFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 重置分类选择
                mCategorySpinner.setSelection(0);
                mCurrentFilterCategory = null;
                // 刷新列表
                refreshNoteList();
            }
        });
    }
    
    /**
     * 初始化排序控件
     */
    private void initSort() {
        mSortSpinner = (Spinner) findViewById(R.id.sort_spinner);
        
        // 创建排序选项列表
        ArrayList<String> sortOptions = new ArrayList<>();
        sortOptions.add("按修改时间排序");
        sortOptions.add("按分类排序");
        sortOptions.add("按待办状态排序");
        
        // 设置排序适配器
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                sortOptions
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSortSpinner.setAdapter(sortAdapter);
        
        // 设置排序选择监听器
        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        // 按修改时间排序
                        mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                        break;
                    case 1:
                        // 按分类排序
                        mCurrentSortOrder = NotePad.Notes.COLUMN_NAME_CATEGORY + " ASC";
                        break;
                    case 2:
                        // 按待办状态排序
                        mCurrentSortOrder = NotePad.Notes.COLUMN_NAME_TODO_STATUS + " ASC";
                        break;
                    default:
                        mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                        break;
                }
                // 刷新列表
                refreshNoteList();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 什么都不做
            }
        });
    }
    
    /**
     * 刷新笔记列表，根据当前搜索条件和分类筛选查询数据
     */
    private void refreshNoteList() {
        // 根据搜索条件和分类筛选构建where子句
        ArrayList<String> selectionParts = new ArrayList<>();
        ArrayList<String> selectionArgList = new ArrayList<>();

        if (mSearchFilter != null && !mSearchFilter.isEmpty()) {
            // 搜索标题或内容包含搜索字符串的笔记
            selectionParts.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
            selectionArgList.add("%" + mSearchFilter + "%");
            selectionArgList.add("%" + mSearchFilter + "%");
        }
        
        // 如果有分类筛选，添加到查询条件
        if (mCurrentFilterCategory != null) {
            selectionParts.add(NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?");
            selectionArgList.add(mCurrentFilterCategory);
        }
        
        // 组合查询条件
        String where = null;
        String[] whereArgs = null;
        if (!selectionParts.isEmpty()) {
            where = TextUtils.join(" AND ", selectionParts);
            whereArgs = selectionArgList.toArray(new String[selectionArgList.size()]);
        }

        // 关闭旧的Cursor
        if (mCursor != null) {
            mCursor.close();
        }

        // 使用普通的query方法替代废弃的managedQuery
        mCursor = getContentResolver().query(
            getIntent().getData(),            // Use the default content URI for the provider.
            PROJECTION,                       // Return the note ID, title and modification date for each note.
            where,                            // Where clause based on search filter
            whereArgs,                        // Where clause arguments
            mCurrentSortOrder                 // Use the current sort order.
        );

        // 更新适配器数据
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
        if (adapter == null) {
            // 如果适配器还不存在，创建一个新的
            adapter = new SimpleCursorAdapter(
                    this,
                    R.layout.noteslist_item,
                    mCursor,
                    new String[] { NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_CATEGORY },
                    new int[] { android.R.id.text1, R.id.text2, R.id.category_text }
            ) {
                @Override
                public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);

                // 格式化时间戳并添加到笔记内容末尾
                TextView noteView = (TextView) view.findViewById(R.id.text2);
                String noteContent = cursor.getString(COLUMN_INDEX_NOTE);
                long timestamp = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
                Date date = new Date(timestamp);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String dateText = dateFormat.format(date);
                noteView.setText(noteContent + "\n" + dateText);
                
                // 应用字体大小
                TextView titleView = (TextView) view.findViewById(android.R.id.text1);
                TextView categoryTextView = (TextView) view.findViewById(R.id.category_text);
                ThemeManager.applyFontSize(titleView, NotesList.this);
                ThemeManager.applyFontSize(noteView, NotesList.this);
                ThemeManager.applyFontSize(categoryTextView, NotesList.this);
                
                // 获取待办状态
                int todoStatus = cursor.getInt(COLUMN_INDEX_TODO_STATUS);
                ImageView todoIcon = (ImageView) view.findViewById(R.id.todo_icon);
                
                // 获取当前笔记ID
                final long noteId = cursor.getLong(0); // _ID is at index 0
                
                // 根据待办状态设置不同的显示效果
                switch (todoStatus) {
                    case NotePad.Notes.TODO_STATUS_PENDING:
                        // 待办状态：显示黄色图标，正常文字
                        todoIcon.setImageResource(R.drawable.ic_todo_pending);
                        todoIcon.setVisibility(View.VISIBLE);
                        titleView.setPaintFlags(titleView.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                        break;
                    case NotePad.Notes.TODO_STATUS_COMPLETED:
                        // 已完成状态：显示绿色图标，添加删除线
                        todoIcon.setImageResource(R.drawable.ic_todo_completed);
                        todoIcon.setVisibility(View.VISIBLE);
                        titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                        break;
                    default: // TODO_STATUS_NONE
                        // 普通状态：显示灰色图标，正常文字
                        todoIcon.setImageResource(R.drawable.ic_todo_unchecked);
                        todoIcon.setVisibility(View.VISIBLE);
                        titleView.setPaintFlags(titleView.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                        break;
                }
                
                // 设置点击事件，实现待办状态切换
                todoIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 从数据库重新获取当前待办状态
                        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), noteId);
                        Cursor cursor = getContentResolver().query(
                                noteUri,
                                new String[]{NotePad.Notes.COLUMN_NAME_TODO_STATUS},
                                null,
                                null,
                                null
                        );
                        
                        int currentTodoStatus = NotePad.Notes.TODO_STATUS_NONE;
                        if (cursor != null && cursor.moveToFirst()) {
                            currentTodoStatus = cursor.getInt(0);
                            cursor.close();
                        }
                        
                        // 切换待办状态
                        int newStatus;
                        if (currentTodoStatus == NotePad.Notes.TODO_STATUS_COMPLETED) {
                            newStatus = NotePad.Notes.TODO_STATUS_PENDING; // 已完成 -> 待处理
                        } else if (currentTodoStatus == NotePad.Notes.TODO_STATUS_PENDING) {
                            newStatus = NotePad.Notes.TODO_STATUS_COMPLETED; // 待处理 -> 已完成
                        } else {
                            newStatus = NotePad.Notes.TODO_STATUS_PENDING; // 非待办 -> 待处理
                        }
                        
                        // 更新待办状态
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, newStatus);
                        
                        getContentResolver().update(
                                noteUri,
                                values,
                                null,
                                null
                        );
                        
                        // 刷新列表
                        refreshNoteList();
                    }
                });
                
                // 处理分类显示
                TextView categoryText = (TextView) view.findViewById(R.id.category_text);
                View categoryColorIndicator = view.findViewById(R.id.category_color_indicator);
                String category = cursor.getString(COLUMN_INDEX_CATEGORY);
                String categoryColor = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR));
                
                if (category != null && !category.isEmpty()) {
                    categoryText.setText(category);
                    categoryText.setVisibility(View.VISIBLE);
                } else {
                    categoryText.setVisibility(View.GONE);
                }
                
                // 设置分类颜色指示器
                if (categoryColor != null && !categoryColor.isEmpty()) {
                    categoryColorIndicator.setBackgroundColor(Color.parseColor(categoryColor));
                    categoryColorIndicator.setVisibility(View.VISIBLE);
                } else {
                    categoryColorIndicator.setVisibility(View.GONE);
                }
                
                // 清除之前可能设置的复合图标
                titleView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                

            }
            };
            setListAdapter(adapter);
        } else {
            // 如果适配器已存在，更新其数据
            adapter.changeCursor(mCursor);
        }
    }

    /**
     * 生成随机颜色
     */
    private String generateRandomColor() {
        // 预定义一组美观的颜色
        String[] colors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E2",
            "#F8C471", "#82E0AA", "#F1948A", "#AED6F1", "#F9E79F",
            "#D2B4DE", "#A3E4D7", "#FAD7A0", "#D7BDE2", "#A9CCE3"
        };
        // 随机选择一种颜色
        Random random = new Random();
        return colors[random.nextInt(colors.length)];
    }
    
    /**
     * 显示搜索对话框
     */
    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("搜索笔记");

        // 创建搜索输入框
        final EditText input = new EditText(this);
        if (mSearchFilter != null) {
            input.setText(mSearchFilter);
        }
        builder.setView(input);

        // 设置按钮
        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSearchFilter = input.getText().toString();
                refreshNoteList();
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.setNeutralButton("清除搜索", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSearchFilter = null;
                input.setText("");
                refreshNoteList();
            }
        });

        builder.show();
    }
    


    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            /*
             * Launches a new Activity using an Intent. The intent filter for the Activity
             * has to have action ACTION_INSERT. No category is set, so DEFAULT is assumed.
             * In effect, this starts the NoteEditor Activity in NotePad.
             */
            // 使用显式Intent启动NoteEditor
            Intent insertIntent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
            insertIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(insertIntent);
            return true;

        } else if (item.getItemId() == R.id.menu_search) {
            // 显示搜索对话框
            showSearchDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_manage_categories) {
            // 显示分类管理对话框
            showCategoryManagerDialog();
            return true;

        } else if (item.getItemId() == R.id.menu_background_white) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_WHITE);
            recreate(); // 重新创建Activity以应用新背景颜色
            return true;
        } else if (item.getItemId() == R.id.menu_background_blue) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_BLUE);
            recreate(); // 重新创建Activity以应用新背景颜色
            return true;
        } else if (item.getItemId() == R.id.menu_background_yellow) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_YELLOW);
            recreate(); // 重新创建Activity以应用新背景颜色
            return true;
        } else if (item.getItemId() == R.id.menu_background_pink) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_PINK);
            recreate(); // 重新创建Activity以应用新背景颜色
            return true;
        } else if (item.getItemId() == R.id.menu_background_green) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_GREEN);
            recreate(); // 重新创建Activity以应用新背景颜色
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_small) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_SMALL);
            recreate(); // 重新创建Activity以应用新字体大小
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_medium) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_MEDIUM);
            recreate(); // 重新创建Activity以应用新字体大小
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_large) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_LARGE);
            recreate(); // 重新创建Activity以应用新字体大小
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_xlarge) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_XLARGE);
            recreate(); // 重新创建Activity以应用新字体大小
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 显示分类管理对话框
     */
    private void showCategoryManagerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("分类管理");
        
        // 创建对话框布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_manager, null);
        builder.setView(dialogView);
        
        // 获取列表视图
        final ListView categoryListView = (ListView) dialogView.findViewById(R.id.category_list);
        final EditText newCategoryName = (EditText) dialogView.findViewById(R.id.new_category_name);
        final Button addCategoryButton = (Button) dialogView.findViewById(R.id.add_category_button);
        
        // 查询所有分类
        final Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                null, null, null);
        
        // 创建Adapter显示分类列表
        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.category_item,
                cursor,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                new int[]{R.id.category_name, R.id.category_color});
        
        // 自定义bindView处理分类颜色显示
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == cursor.getColumnIndex(NotePad.Categories.COLUMN_NAME_COLOR)) {
                    String color = cursor.getString(columnIndex);
                    View colorIndicator = view.findViewById(R.id.category_color);
                    colorIndicator.setBackgroundColor(Color.parseColor(color));
                    return true;
                }
                return false;
            }
        });
        
        // 设置列表适配器
        categoryListView.setAdapter(adapter);
        
        // 为列表项添加删除功能
        categoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 获取点击的分类信息
                Cursor itemCursor = (Cursor) adapter.getItem(position);
                if (itemCursor != null) {
                    final String categoryId = itemCursor.getString(itemCursor.getColumnIndex(NotePad.Categories._ID));
                    final String categoryName = itemCursor.getString(itemCursor.getColumnIndex(NotePad.Categories.COLUMN_NAME_NAME));
                    
                    // 不允许删除默认的"其他"分类
                    if ("其他".equals(categoryName)) {
                        Toast.makeText(NotesList.this, "不能删除默认分类", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 确认删除对话框
                    AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(NotesList.this);
                    confirmBuilder.setTitle("确认删除");
                    confirmBuilder.setMessage("确定要删除分类\"" + categoryName + "\"吗？使用该分类的笔记将被移至'其他'分类。");
                    confirmBuilder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 删除分类
                            Uri categoryUri = ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, Long.parseLong(categoryId));
                            int deletedRows = getContentResolver().delete(categoryUri, null, null);
                            
                            if (deletedRows > 0) {
                                Toast.makeText(NotesList.this, "分类删除成功", Toast.LENGTH_SHORT).show();
                                // 刷新分类列表
                                Cursor newCursor = getContentResolver().query(
                                        NotePad.Categories.CONTENT_URI,
                                        new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                                        null, null, null);
                                adapter.changeCursor(newCursor);
                                
                                // 刷新笔记列表以更新分类筛选器
                                refreshNoteList();
                            } else {
                                Toast.makeText(NotesList.this, "分类删除失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    confirmBuilder.setNegativeButton("取消", null);
                    confirmBuilder.show();
                }
            }
        });
        
        // 添加分类按钮点击事件
        addCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String categoryName = newCategoryName.getText().toString().trim();
                if (!categoryName.isEmpty()) {
                    // 检查分类是否已存在
                    Cursor checkCursor = getContentResolver().query(
                            NotePad.Categories.CONTENT_URI,
                            new String[]{NotePad.Categories._ID},
                            NotePad.Categories.COLUMN_NAME_NAME + " = ?",
                            new String[]{categoryName},
                            null);
                    
                    boolean exists = checkCursor != null && checkCursor.getCount() > 0;
                    if (checkCursor != null) {
                        checkCursor.close();
                    }
                    
                    if (exists) {
                        Toast.makeText(NotesList.this, "分类已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        // 插入新分类，随机生成颜色
                    ContentValues values = new ContentValues();
                    values.put(NotePad.Categories.COLUMN_NAME_NAME, categoryName);
                    values.put(NotePad.Categories.COLUMN_NAME_COLOR, generateRandomColor());
                        
                        getContentResolver().insert(NotePad.Categories.CONTENT_URI, values);
                        
                        // 刷新分类列表
                        Cursor newCursor = getContentResolver().query(
                                NotePad.Categories.CONTENT_URI,
                                new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                                null, null, null);
                        adapter.changeCursor(newCursor);
                        
                        // 清空输入框
                        newCategoryName.setText("");
                        Toast.makeText(NotesList.this, "分类添加成功", Toast.LENGTH_SHORT).show();
                        
                        // 刷新笔记列表以更新分类筛选器
                        refreshNoteList();
                    }
                } else {
                    Toast.makeText(NotesList.this, "请输入分类名称", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // 设置对话框按钮
        builder.setPositiveButton("关闭", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (cursor != null) {
                    cursor.close();
                }
                dialog.dismiss();
            }
        });
        
        // 创建并显示对话框
        final AlertDialog dialog = builder.create();
        
        // 对话框关闭时关闭cursor并重新初始化分类筛选器
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (cursor != null) {
                    cursor.close();
                }
                // 重新初始化分类筛选器，确保显示最新分类
                initCategoryFilter();
            }
        });
        
        dialog.show();
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            // 使用显式Intent启动NoteEditor
            Intent editIntent = new Intent(Intent.ACTION_EDIT, noteUri);
            editIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(editIntent);
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // Sends out an Intent to start an Activity that can handle ACTION_EDIT. The
            // Intent's data is the note ID URI. The effect is to call NoteEdit.
            // 使用显式Intent启动NoteEditor
            Intent editIntent = new Intent(Intent.ACTION_EDIT, uri);
            editIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(editIntent);
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 刷新列表以适应配置变化
        refreshNoteList();
    }
}
