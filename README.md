## NewMusic
> music new UI 开发仓库。   
> 仓库地址：http://10.0.3.32:3000/DAPS_APP2/DreamMusic.git

## optimization分支主要修改
* 新建数据加载以及数据操作模块，将数据查询和相关操作统一到一起，避免出现数据库频繁查询导致的系统gc频繁调用，避免
gc导致的ANR。
* 代码重用（相关的数据操作：添加播放列表，删除文件，文件的option操作等），增加后续代码的可维护性。
* 删除不必要的代码和资源。

## 可以按下面的两种方法import代码到android studio

1. 点击菜单栏“File”--"New"--"project from version control"--"Git" -- 在URL一栏输入仓库地址-- 点击确认。
2. 点击菜单栏“File”--"New"--"Import Project"--在文件选择框选择下载的仓库目录即可。   

**若需要在studio里编译APK。直接将SprdFrameworks.java中的override方法以及无法识别的import全部注释掉即可。**

## 修改代码以及提交时请注意
1. 如果需要添加Hide类型或者sprd private API 请在SprdFrameworks.java中进行封装后使用。
2. 后续使用高SDK api时，请使用下面的方法进行判断，以免影响在低android版本的使用。   
```
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    ……
}
```   
3. 提交代码前请使用“Ctrl+Alt+l”快捷键进行format code.

## 新增的主要feature
* 定时关闭，支持定时关闭音乐应用，提供定时时间供用户选择。
* 歌曲可收藏，并添加到收藏播放列表
* 增加文件夹视图。
* 歌曲快捷显示条：当有歌曲正在播放时，在页面最下方显示歌曲播放快捷显示条，该快捷条能实现切换，控制音乐播放，快速进入播放页面等功能。
* OTG音乐的单独显示
* 状态栏/锁屏播放条上支持更多功能：上一首/下一首，暂停，播放。
* 换肤,设置中支持换肤功能。