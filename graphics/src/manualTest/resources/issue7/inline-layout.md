# 行内排版验收

普通文字`inline_code`后续文字，连续代码 `first`、`second`；🚀 Emoji 后的 `code` 也应有完整圆角。

混排文字 `code` 与 **粗体**、*斜体*，后面继续文字，再接 `tail`。

较长代码 `abcdefghijklmnopqrstuvwxyz_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789_abcdefghijklmnopqrstuvwxyz` 自动换行，前后文字不被背景遮住。

- [x] 已完成任务，包含 `inline_code`
- [ ] 待完成任务，包含 `long_identifier` 和后续文字
  - [x] 嵌套任务，文字足够长时换行，图标仍与第一行对齐，而不是跟随整个任务项居中。

正文中的美元符号：$5、$a+b$ 和 $$，按普通 Markdown 文本显示。

~~~kotlin
val message = "保留  空格"
    println(message)
~~~
