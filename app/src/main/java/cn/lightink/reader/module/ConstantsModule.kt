package cn.lightink.reader.module

const val EMPTY = ""

const val LIMIT = 20

//Intent Key
const val INTENT_BOOK = "book"
const val INTENT_BOOK_RANK = "book_rank"
const val INTENT_BOOK_SOURCE = "book_source"
const val INTENT_BOOK_CACHE = "book_cache"
const val INTENT_FEED_GROUP = "feed_group"
const val INTENT_FEED = "feed"
const val INTENT_FEED_FLOW = "feed_flow"
const val INTENT_SEARCH = "search"
const val INTENT_BOOKSHELF = "bookshelf"
const val INTENT_PROGRESS = "progress"
const val INTENT_THEME = "theme"
const val INTENT_CHATS = "chats"
const val INTENT_BOOKMARK_CHAPTER = "bookmark_chapter"
const val INTENT_BOOKMARK_PROGRESS = "bookmark_progress"
const val INTENT_ACTION = "action"

//Worker
const val CHASE_UPDATE_TAG = "ChaseUpdate"

//书籍状态
//完结
const val BOOK_STATE_END = -1
//正常
const val BOOK_STATE_IDLE = 0
//更新
const val BOOK_STATE_UPDATE = 1

const val TOAST_TYPE_SUCCESS = 1
const val TOAST_TYPE_FAILURE = 2
const val TOAST_TYPE_WARNING = 3

//访问失败
const val GET_FAILED_NET_THROWABLE = "NET_THROWABLE"
//访问失败 未授权
const val GET_FAILED_INVALID_AUTH = "INVALID_AUTH"
//访问失败 未订阅
const val GET_FAILED_INVALID_AUTH_BUY = "INVALID_AUTH_BUY"
//分卷
const val GET_FAILED_IS_BOOKLET = "IS_BOOKLET"

const val READER_RESULT_RESTART = 8
const val READER_RESULT_RECREATE = 9
const val READER_RESULT_BOOKMARK = 10

const val TEST_STRING1 = "测试文字"
const val TEST_STRING2 = "测试\n文字"

//正则：匹配图片值
const val REGEX_IMAGE_VALUE = """(?<=\().+(?=\))"""
//正则：匹配图片注解
const val REGEX_IMAGE_ALT = """(?<=\[).+(?=\])"""
//正则：匹配图片
const val REGEX_IMAGE_MATCH = """\n*\u3000*!\[.*]\(.+\)\n*"""

const val READ_MAX_PAGE_OF_SECOND = 180
const val MILLISECOND = 1000

const val MP_FOLDER_IMAGES = "images"
const val MP_FOLDER_TEXTS = "texts"
const val MP_FOLDER_FILES = "files"

const val MP_FILENAME_CATALOG = "catalog.md"
const val MP_FILENAME_METADATA = "metadata.json"
const val MP_FILENAME_BOOK_SOURCE = "source.json"
const val MP_FILENAME_PURIFY = "purify.json"

const val MP_CATALOG_INDENTATION = "\t"
const val MP_ENTER = "\n"

//HTML元素
const val H1 = "h1"
const val H2 = "h2"
const val H3 = "h3"
const val H4 = "h4"
const val H5 = "h5"
const val H6 = "h6"
const val BR = "br"
const val P = "p"
const val A = "a"
const val IMG = "img"
const val DIV = "div"
const val SCRIPT = "script"
const val STYLE = "style"
const val TEXT = "#text"
const val FIGURE = "figure"
const val FIGCAPTION = "figcaption"
const val BODY = "body"