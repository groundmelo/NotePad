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

public class NotesList extends ListActivity {

    private static final String TAG = "NotesList";

    private String mSearchFilter = null;

    private static final int SEARCH_REQUEST = 1;

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_TODO_STATUS,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_CATEGORY_COLOR
    };

    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_NOTE = 2;
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 3;
    private static final int COLUMN_INDEX_TODO_STATUS = 4;
    private static final int COLUMN_INDEX_CATEGORY = 5;
    private static final int COL_CATEGORY_COLOR_INDEX = 6;

    private Spinner mCategorySpinner;
    private Button mClearFilterButton;
    private String mCurrentFilterCategory = null;

    private Spinner mSortSpinner;
    private String mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;

    private Cursor mCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);

        setContentView(R.layout.noteslist);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();

        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        initCategoryFilter();

        initSort();

        refreshNoteList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCursor == null || mCursor.isClosed()) {
            refreshNoteList();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        SubMenu backgroundMenu = menu.addSubMenu("切换背景颜色");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_white, Menu.NONE, "白色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_blue, Menu.NONE, "蓝色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_yellow, Menu.NONE, "黄色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_pink, Menu.NONE, "粉色背景");
        backgroundMenu.add(Menu.NONE, R.id.menu_background_green, Menu.NONE, "绿色背景");

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final boolean haveItems = getListAdapter().getCount() > 0;

        if (haveItems) {

            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            Intent[] specifics = new Intent[1];

            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            MenuItem[] items = new MenuItem[1];

            Intent intent = new Intent(null, uri);

            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    Menu.NONE,
                    Menu.NONE,
                    null,
                    specifics,
                    intent,
                    Menu.NONE,
                    items
            );
            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        return true;
    }

    private void initCategoryFilter() {
        mCategorySpinner = (Spinner) findViewById(R.id.category_spinner);
        mClearFilterButton = (Button) findViewById(R.id.clear_filter_button);

        Cursor categoryCursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME},
                null,
                null,
                NotePad.Categories.COLUMN_NAME_NAME + " ASC"
        );

        ArrayList<String> categories = new ArrayList<>();
        categories.add("全部");

        if (categoryCursor != null && categoryCursor.moveToFirst()) {
            do {
                String category = categoryCursor.getString(0);
                if (category != null) {
                    categories.add(category);
                }
            } while (categoryCursor.moveToNext());
            categoryCursor.close();
        } else if (categoryCursor != null) {
            categoryCursor.close();
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categories
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(categoryAdapter);

        mCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mCurrentFilterCategory = null;
                } else {
                    mCurrentFilterCategory = (String) parent.getItemAtPosition(position);
                }
                refreshNoteList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mClearFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCategorySpinner.setSelection(0);
                mCurrentFilterCategory = null;
                refreshNoteList();
            }
        });
    }

    private void initSort() {
        mSortSpinner = (Spinner) findViewById(R.id.sort_spinner);

        ArrayList<String> sortOptions = new ArrayList<>();
        sortOptions.add("按修改时间排序");
        sortOptions.add("按分类排序");
        sortOptions.add("按待办状态排序");

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                sortOptions
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSortSpinner.setAdapter(sortAdapter);

        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                        break;
                    case 1:
                        mCurrentSortOrder = NotePad.Notes.COLUMN_NAME_CATEGORY + " ASC";
                        break;
                    case 2:
                        mCurrentSortOrder = NotePad.Notes.COLUMN_NAME_TODO_STATUS + " ASC";
                        break;
                    default:
                        mCurrentSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                        break;
                }
                refreshNoteList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshNoteList() {
        ArrayList<String> selectionParts = new ArrayList<>();
        ArrayList<String> selectionArgList = new ArrayList<>();

        if (mSearchFilter != null && !mSearchFilter.isEmpty()) {
            selectionParts.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
            selectionArgList.add("%" + mSearchFilter + "%");
            selectionArgList.add("%" + mSearchFilter + "%");
        }

        if (mCurrentFilterCategory != null) {
            selectionParts.add(NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?");
            selectionArgList.add(mCurrentFilterCategory);
        }

        String where = null;
        String[] whereArgs = null;
        if (!selectionParts.isEmpty()) {
            where = TextUtils.join(" AND ", selectionParts);
            whereArgs = selectionArgList.toArray(new String[selectionArgList.size()]);
        }

        if (mCursor != null) {
            mCursor.close();
        }

        mCursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                where,
                whereArgs,
                mCurrentSortOrder
        );

        SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
        if (adapter == null) {
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

                    TextView noteView = (TextView) view.findViewById(R.id.text2);
                    String noteContent = cursor.getString(COLUMN_INDEX_NOTE);
                    long timestamp = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
                    Date date = new Date(timestamp);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String dateText = dateFormat.format(date);
                    noteView.setText(noteContent + "\n" + dateText);

                    TextView titleView = (TextView) view.findViewById(android.R.id.text1);
                    TextView categoryTextView = (TextView) view.findViewById(R.id.category_text);
                    ThemeManager.applyFontSize(titleView, NotesList.this);
                    ThemeManager.applyFontSize(noteView, NotesList.this);
                    ThemeManager.applyFontSize(categoryTextView, NotesList.this);

                    int todoStatus = cursor.getInt(COLUMN_INDEX_TODO_STATUS);
                    ImageView todoIcon = (ImageView) view.findViewById(R.id.todo_icon);

                    final long noteId = cursor.getLong(0);

                    switch (todoStatus) {
                        case NotePad.Notes.TODO_STATUS_PENDING:
                            todoIcon.setImageResource(R.drawable.ic_todo_pending);
                            todoIcon.setVisibility(View.VISIBLE);
                            titleView.setPaintFlags(titleView.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                            break;
                        case NotePad.Notes.TODO_STATUS_COMPLETED:
                            todoIcon.setImageResource(R.drawable.ic_todo_completed);
                            todoIcon.setVisibility(View.VISIBLE);
                            titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                            break;
                        default:
                            todoIcon.setImageResource(R.drawable.ic_todo_unchecked);
                            todoIcon.setVisibility(View.VISIBLE);
                            titleView.setPaintFlags(titleView.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                            break;
                    }

                    todoIcon.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
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

                            int newStatus;
                            if (currentTodoStatus == NotePad.Notes.TODO_STATUS_COMPLETED) {
                                newStatus = NotePad.Notes.TODO_STATUS_PENDING;
                            } else if (currentTodoStatus == NotePad.Notes.TODO_STATUS_PENDING) {
                                newStatus = NotePad.Notes.TODO_STATUS_COMPLETED;
                            } else {
                                newStatus = NotePad.Notes.TODO_STATUS_PENDING;
                            }

                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put(NotePad.Notes.COLUMN_NAME_TODO_STATUS, newStatus);

                            getContentResolver().update(
                                    noteUri,
                                    values,
                                    null,
                                    null
                            );

                            refreshNoteList();
                        }
                    });

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

                    if (categoryColor != null && !categoryColor.isEmpty()) {
                        categoryColorIndicator.setBackgroundColor(Color.parseColor(categoryColor));
                        categoryColorIndicator.setVisibility(View.VISIBLE);
                    } else {
                        categoryColorIndicator.setVisibility(View.GONE);
                    }

                    titleView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
            };
            setListAdapter(adapter);
        } else {
            adapter.changeCursor(mCursor);
        }
    }

    private String generateRandomColor() {
        String[] colors = {
                "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
                "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E2",
                "#F8C471", "#82E0AA", "#F1948A", "#AED6F1", "#F9E79F",
                "#D2B4DE", "#A3E4D7", "#FAD7A0", "#D7BDE2", "#A9CCE3"
        };
        Random random = new Random();
        return colors[random.nextInt(colors.length)];
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("搜索笔记");

        final EditText input = new EditText(this);
        if (mSearchFilter != null) {
            input.setText(mSearchFilter);
        }
        builder.setView(input);

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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            Intent insertIntent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
            insertIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(insertIntent);
            return true;

        } else if (item.getItemId() == R.id.menu_search) {
            showSearchDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_manage_categories) {
            showCategoryManagerDialog();
            return true;

        } else if (item.getItemId() == R.id.menu_background_white) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_WHITE);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_background_blue) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_BLUE);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_background_yellow) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_YELLOW);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_background_pink) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_PINK);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_background_green) {
            ThemeManager.setBackgroundColorMode(this, ThemeManager.BACKGROUND_GREEN);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_small) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_SMALL);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_medium) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_MEDIUM);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_large) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_LARGE);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.menu_font_size_xlarge) {
            ThemeManager.setFontSizeMode(this, ThemeManager.FONT_SIZE_XLARGE);
            recreate();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showCategoryManagerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("分类管理");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_manager, null);
        builder.setView(dialogView);

        final ListView categoryListView = (ListView) dialogView.findViewById(R.id.category_list);
        final EditText newCategoryName = (EditText) dialogView.findViewById(R.id.new_category_name);
        final Button addCategoryButton = (Button) dialogView.findViewById(R.id.add_category_button);

        final Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                null, null, null);

        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.category_item,
                cursor,
                new String[]{NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                new int[]{R.id.category_name, R.id.category_color});

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

        categoryListView.setAdapter(adapter);

        categoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor itemCursor = (Cursor) adapter.getItem(position);
                if (itemCursor != null) {
                    final String categoryId = itemCursor.getString(itemCursor.getColumnIndex(NotePad.Categories._ID));
                    final String categoryName = itemCursor.getString(itemCursor.getColumnIndex(NotePad.Categories.COLUMN_NAME_NAME));

                    if ("其他".equals(categoryName)) {
                        Toast.makeText(NotesList.this, "不能删除默认分类", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(NotesList.this);
                    confirmBuilder.setTitle("确认删除");
                    confirmBuilder.setMessage("确定要删除分类\"" + categoryName + "\"吗？使用该分类的笔记将被移至'其他'分类。");
                    confirmBuilder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Uri categoryUri = ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, Long.parseLong(categoryId));
                            int deletedRows = getContentResolver().delete(categoryUri, null, null);

                            if (deletedRows > 0) {
                                Toast.makeText(NotesList.this, "分类删除成功", Toast.LENGTH_SHORT).show();
                                Cursor newCursor = getContentResolver().query(
                                        NotePad.Categories.CONTENT_URI,
                                        new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                                        null, null, null);
                                adapter.changeCursor(newCursor);

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

        addCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String categoryName = newCategoryName.getText().toString().trim();
                if (!categoryName.isEmpty()) {
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
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Categories.COLUMN_NAME_NAME, categoryName);
                        values.put(NotePad.Categories.COLUMN_NAME_COLOR, generateRandomColor());

                        getContentResolver().insert(NotePad.Categories.CONTENT_URI, values);

                        Cursor newCursor = getContentResolver().query(
                                NotePad.Categories.CONTENT_URI,
                                new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_COLOR},
                                null, null, null);
                        adapter.changeCursor(newCursor);

                        newCategoryName.setText("");
                        Toast.makeText(NotesList.this, "分类添加成功", Toast.LENGTH_SHORT).show();

                        refreshNoteList();
                    }
                } else {
                    Toast.makeText(NotesList.this, "请输入分类名称", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setPositiveButton("关闭", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (cursor != null) {
                    cursor.close();
                }
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.create();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (cursor != null) {
                    cursor.close();
                }
                initCategoryFilter();
            }
        });

        dialog.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        if (cursor == null) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        int id = item.getItemId();
        if (id == R.id.context_open) {
            Intent editIntent = new Intent(Intent.ACTION_EDIT, noteUri);
            editIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(editIntent);
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));

            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(
                    noteUri,
                    null,
                    null
            );

            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            Intent editIntent = new Intent(Intent.ACTION_EDIT, uri);
            editIntent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivity(editIntent);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshNoteList();
    }
}