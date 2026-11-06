# 中文 Markdown 渲染验收

中文与 Unicode：你好，Tavolo。Emoji：😀 🚀 👨‍👩‍👧‍👦 🇨🇳。

## 行内样式与换行

普通文字、**粗体**、*斜体*、***粗斜体***、~~删除线~~、**粗体中的 ~~删除线~~ 与 `inline code`**，相邻代码 `left``right`，含反引号的代码 ``a ` b``。
这是软换行后的中文。  
这是硬换行后的中文。

链接：[Tavolo 项目说明](https://github.com/4o4E/Tavolo)，文字和目标都必须可见。

### 非 1 起始及嵌套列表

7. 第七项
   - 第一层无序项
     - 第二层无序项，包含 **强调** 和 `code`
   - 另一项
8. 第八项，编号不能重置为一。

#### 引用与长内容

> 引用中的段落，宽度必须扣除边框和内边距。这段中文重复描述换行边界，使文字始终留在引用容器以内，不会跑出右边界。
>
> > 嵌套引用：中文、**粗体** 与 ~~删除线~~。
>
> 引用里的代码：
>
> ```text
>   spaces  stay   here; `backticks` stay too
>   a_very_long_identifier_that_must_wrap_without_losing_characters_012345678901234567890123456789
> ```

##### GFM 表格

| 名称 | 说明 | 数值 |
| :--- | :---: | ---: |
| 中文行一 | **粗体**与`code` | 31.87% |
| 中文行二 | 很长的表格内容，需要在当前列内换行，不能越过相邻单元格。 | 89% |
| 链接 | [文档](https://example.com/docs) | ~~旧值~~ |

###### 代码与资源

~~~kotlin
val message = "中文  with   spaces"
val tick = "`one` and ``two``"
    val longLine = "abcdefghijklmnopqrstuvwxyz_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789_abcdefghijklmnopqrstuvwxyz_ABCDEFGHIJKLMNOPQRSTUVWXYZ"
~~~

    indented  code   keeps spaces
    `literal` and 中文

---

图片由调用方解析，不隐式下载：

![调用方提供的离线图片](fixture:avatar)

图片之后的内容仍然保留。

<script>alert('输入脚本不会执行，也不会触发资源读取')</script>

~~~text
普通代码块：a + b = c
  连续  空格与缩进保持
~~~

末尾完整性标记：文档结束。
