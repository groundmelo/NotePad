# NotePad应用功能升级PRD

## 1. 项目概述

### 1.1 项目背景
当前NotePad应用是一个基础的笔记应用，提供了创建、编辑和删除笔记的功能。为了提升用户体验和功能性，需要对应用进行升级，添加时间戳显示、查询功能、笔记分类、UI美化和笔记排序等功能。

### 1.2 项目目标
- 增强应用的功能性，满足用户更多需求
- 提升应用的用户体验和视觉效果
- 保持应用的简洁性和易用性

## 2. 功能需求

### 2.1 基本功能升级

#### 2.1.1 笔记条目时间戳显示
- **功能描述**：在笔记列表界面的每个笔记条目上显示最后修改时间
- **实现细节**：
  - 时间格式：yyyy-MM-dd HH:mm:ss
  - 显示位置：笔记标题下方
  - 字体大小：小于标题字体
  - 字体颜色：灰色

#### 2.1.2 笔记查询功能
- **功能描述**：添加搜索框，允许用户根据标题或内容搜索笔记
- **实现细节**：
  - 搜索框位置：笔记列表界面顶部
  - 搜索触发方式：实时搜索（输入时自动搜索）
  - 搜索范围：标题和内容
  - 搜索结果：即时显示匹配的笔记
  - 清空搜索：提供清空按钮

### 2.2 附加功能

#### 2.2.1 笔记分类
- **功能描述**：允许用户为笔记添加分类标签，便于组织和管理
- **实现细节**：
  - 分类管理：
    - 预设分类：工作、学习、生活、其他
    - 支持自定义分类
    - 分类编辑：添加、删除、重命名
  - 分类使用：
    - 创建/编辑笔记时可选择分类
    - 笔记列表可按分类筛选
    - 笔记条目显示分类标签
  - 分类颜色：每个分类有对应的颜色标识

#### 2.2.2 笔记代办功能
- **功能描述**：允许用户将笔记标记为待办事项，并跟踪其完成状态
- **实现细节**：
  - 待办状态：
    - 普通笔记（默认）
    - 待办事项（未完成）
    - 待办事项（已完成）
  - 待办功能：
    - 创建/编辑笔记时可设置待办状态
    - 笔记列表中显示待办状态标识
    - 支持通过点击切换待办状态
  - 视觉标识：
    - 未完成待办：显示复选框（未勾选）
    - 已完成待办：显示复选框（已勾选），内容可选择性添加删除线

#### 2.2.3 UI美化
- **功能描述**：优化应用的视觉效果和用户体验
- **实现细节**：
  - 主题设计：
    - 支持浅色/深色主题切换
    - 现代化的UI设计
    - 统一的色彩方案
  - 界面优化：
    - 笔记列表卡片式布局
    - 圆角设计
    - 适当的阴影效果
    - 流畅的过渡动画
  - 图标更新：
    - 使用Material Design图标
    - 统一的图标风格

#### 2.2.4 笔记排序
- **功能描述**：允许用户根据不同条件对笔记进行排序
- **实现细节**：
  - 排序选项：
    - 按修改时间（默认，降序）
    - 按创建时间（升序/降序）
    - 按标题（字母顺序，升序/降序）
    - 按分类（字母顺序，升序/降序）
    - 按待办状态（未完成优先）
  - 排序触发：
    - 菜单选项中提供排序选择
    - 支持快速切换排序方式

## 3. 数据模型升级

### 3.1 现有数据模型
```java
public static final class Notes implements BaseColumns {
    public static final String COLUMN_NAME_TITLE = "title";
    public static final String COLUMN_NAME_NOTE = "note";
    public static final String COLUMN_NAME_CREATE_DATE = "created";
    public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    public static final String COLUMN_NAME_TODO_STATUS = "todo_status";
}
```

### 3.2 升级后数据模型
```java
public static final class Notes implements BaseColumns {
    public static final String COLUMN_NAME_TITLE = "title";
    public static final String COLUMN_NAME_NOTE = "note";
    public static final String COLUMN_NAME_CREATE_DATE = "created";
    public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    public static final String COLUMN_NAME_TODO_STATUS = "todo_status"; // 待办状态字段
    public static final String COLUMN_NAME_CATEGORY = "category"; // 新增分类字段
    public static final String COLUMN_NAME_CATEGORY_COLOR = "category_color"; // 新增分类颜色字段
}

// 新增分类表
public static final class Categories implements BaseColumns {
    public static final String TABLE_NAME = "categories";
    public static final String COLUMN_NAME_NAME = "name";
    public static final String COLUMN_NAME_COLOR = "color";
}

// 待办状态常量
public static final int TODO_STATUS_NONE = 0;     // 普通笔记
public static final int TODO_STATUS_PENDING = 1;  // 待办事项（未完成）
public static final int TODO_STATUS_COMPLETED = 2; // 待办事项（已完成）
```

## 4. 界面设计

### 4.1 笔记列表界面
- **顶部**：搜索框、排序按钮
- **主体**：笔记列表（卡片式布局）
  - 每个笔记条目包含：分类标签（带颜色）、标题、时间戳、内容预览
- **底部**：添加笔记按钮
- **侧滑菜单**：分类筛选、主题切换

### 4.2 笔记编辑界面
- **顶部**：保存按钮、分类选择按钮
- **主体**：
  - 待办状态切换开关
  - 标题输入
  - 内容输入
- **底部**：功能按钮（加粗、斜体等格式选项）

### 4.3 分类管理界面
- **顶部**：添加分类按钮
- **主体**：分类列表
  - 每个分类条目包含：名称、颜色选择器、删除按钮

## 5. 实现计划

### 5.1 第一阶段：基本功能升级
1. 修改noteslist_item.xml，添加时间戳显示
2. 修改NotesList.java，实现时间戳数据绑定
3. 添加搜索框布局和功能实现
4. 修改NotePadProvider.java，添加搜索功能支持

### 5.2 第二阶段：附加功能实现
1. 数据模型扩展：添加分类相关字段和表
2. 笔记分类功能实现：
   - 分类管理界面
   - 笔记编辑时的分类选择
   - 笔记列表的分类筛选
3. 笔记代办功能实现：
   - 待办状态切换功能
   - 笔记列表的待办状态显示
   - 待办状态切换动画效果
4. UI美化：
   - 更新布局文件，实现卡片式设计
   - 添加主题切换功能
   - 更新图标和色彩方案
5. 笔记排序功能实现：
   - 添加排序选项菜单
   - 实现不同排序方式

### 5.3 第三阶段：测试和优化
1. 功能测试
2. UI/UX优化
3. 性能优化
4. Bug修复

## 6. 技术要点

### 6.1 数据库升级
- 需要处理数据库版本升级，添加新的表和字段
- 使用SQLiteOpenHelper的onUpgrade方法实现

### 6.2 内容提供者扩展
- 需要更新NotePadProvider，支持分类数据的CRUD操作
- 添加搜索功能的查询支持

### 6.3 UI实现
- 使用RecyclerView实现卡片式列表
- 使用Material Design组件优化界面
- 实现流畅的动画效果

### 6.4 主题切换
- 使用Android的主题系统实现浅色/深色主题
- 支持系统主题自动适配

## 7. 预期效果

### 7.1 功能效果
- 用户可以清晰看到笔记的修改时间
- 快速搜索到需要的笔记
- 按分类管理和筛选笔记
- 自定义笔记排序方式
- 享受现代化的UI设计

### 7.2 性能效果
- 搜索功能响应迅速
- 界面流畅，动画效果自然
- 数据库操作高效

## 8. 风险评估

### 8.1 数据库升级风险
- 可能导致现有数据丢失
- 解决方案：
  - 仔细编写数据库升级脚本
  - 进行充分的测试
  - 提供数据备份和恢复功能

### 8.2 UI兼容性风险
- 不同设备和Android版本可能显示不一致
- 解决方案：
  - 使用兼容性库
  - 进行多设备测试
  - 设计自适应布局

### 8.3 性能风险
- 大量笔记时搜索和排序可能变慢
- 解决方案：
  - 优化数据库查询
  - 使用分页加载
  - 实现搜索结果缓存

## 9. 验收标准

### 9.1 基本功能
- 笔记列表中每个条目显示正确的时间戳
- 搜索功能能准确匹配标题和内容
- 搜索结果实时更新

### 9.2 附加功能
- 能成功创建、编辑和删除分类
- 能为笔记分配分类并在列表中显示
- 能按分类筛选笔记
- 能将笔记标记为待办事项并切换状态
- 待办事项在列表中正确显示状态
- UI符合设计规范，美观易用
- 能按不同条件排序笔记（包括按待办状态）

### 9.3 性能要求
- 搜索响应时间<500ms
- 列表滚动流畅，无卡顿
- 应用启动时间<2s

## 10. 后续规划

### 10.1 功能扩展
- 支持图片、语音等多媒体笔记
- 实现笔记云同步
- 添加笔记分享功能
- 实现OCR扫描功能

### 10.2 优化方向
- 进一步优化性能
- 增强用户体验
- 支持更多语言
- 适配更多设备