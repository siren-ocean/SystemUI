## SystemUI from android-11.0.0_r10
### SystemUI脱离源码，并在Android Studio进行编译，最重要的一点是不试图改变它本身的目录结构，而是通过添加额外的配置和依赖，既能支持本地运行，又不影响其在AOSP上使用Android.bp进行系统编译。
### 本项目会在关键的地方，利用脚本移除相关的在AndroidStudio上所不支持的属性和字段，并通过git命令将其忽略(不往仓库上提交)，使其在本地能正常运行的同时，也不影响它在AOSP总体仓库的份量。


## 执行步骤如下
#### 第一步：运行在Filter上的主函数，执行过滤任务
<img src="images/filter_main.png" width = "600" height = "480"/>

### 第二步：执行Android Studio上Build APK的操作, 然后将apk推送到设备上SystemUI所在的目录

```
adb push SystemUI.apk /system/system_ext/priv-app/SystemUI/

adb shell killall com.android.systemui
```
######  如果SystemUI不能正常起来，则需要重启一下设备
```
adb reboot
```

###  测试在pixel2上运行结果：Gradle编译 VS Android.bp编译
<img src="images/pixel2_systemui_gradle.jpg" width = "225" height = "400"/> <img src="images/pixel2_systemui_original.jpg" width = "225" height = "400"/>

---
######  此时我们发现运行的效果会与原生的还是有些许之间的差异，这是由于脱离源码之后，SystemUI在一些private属性上的引用失败所导致的样式差异，目前看来并没有什么特别的办法。

## 构建步骤

### Step1：引入静态依赖
##### @framework.jar:
```
// AOSP/android-11/out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes-header.jar
compileOnly files('libs/framework.jar')
```
![avatar](images/framework.png)

##### @core-all.jar:
```
// AOSP/android-11/out/soong/.intermediates/libcore/core-all/android_common/javac/core-all.jar
compileOnly files('libs/core-all.jar')
```
![avatar](images/core-all.png)


##### @preference-1.2.0-alpha01.aar:
```
// AOSP/android-11/prebuilts/sdk/current/androidx/m2repository/androidx/preference/preference/1.2.0-alpha01/preference-1.2.0-alpha01.aar
implementation(name: 'preference-1.2.0-alpha01', ext: 'aar')
```


![avatar](images/preference-1.2.0-alpha01.png)
###### ps: androidx.preference 不容易通过以下方式去引用，故换成静态
```
## implementation 'androidx.preference:preference:1.2.0-alpha01'
```

##### @iconloader_base.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/libs/systemui/iconloaderlib/iconloader_base/android_common/javac/iconloader_base.jar
implementation files('libs/iconloader_base.jar')
```
![avatar](images/iconloader_base.png)


##### @libprotobuf-java-nano.jar:
```
// AOSP/android-11/out/soong/.intermediates/external/protobuf/libprotobuf-java-nano/android_common/javac/libprotobuf-java-nano.jar
implementation files('libs/libprotobuf-java-nano.jar')
```
![avatar](images/libprotobuf-java-nano.png)


##### @SystemUIPluginLib.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/packages/SystemUI/plugin/SystemUIPluginLib/android_common/javac/SystemUIPluginLib.jar
implementation files('libs/SystemUIPluginLib.jar')
```
![avatar](images/SystemUIPluginLib.png)


##### @SystemUISharedLib.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUISharedLib/android_common/javac/SystemUISharedLib.jar
implementation files('libs/SystemUISharedLib.jar')
```
![avatar](images/SystemUISharedLib.png)


##### @WindowManager-Shell.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar
implementation files('libs/WindowManager-Shell.jar')
```
![avatar](images/WindowManager-Shell.png)


##### @SystemUI-tags.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/packages/SystemUI/SystemUI-tags/android_common/javac/SystemUI-tags.jar
implementation files('libs/SystemUI-tags.jar')
```
![avatar](images/SystemUI-tags.png)



##### @SystemUI-proto.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/packages/SystemUI/SystemUI-proto/android_common/javac/SystemUI-proto.jar
implementation files('libs/SystemUI-proto.jar')
```
![avatar](images/SystemUI-proto.png)


##### @SystemUI-statsd.jar:
```
// AOSP/android-11/out/soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar
implementation files('libs/SystemUI-statsd.jar')
```
![avatar](images/SystemUI-statsd.png)


### Step2：引入Module
###### 将具体路径下的代码直接导入到项目中作为Module依赖, 构建的时候可以直接通过implementation project引用，或者也可以gradle build生成aar,再放置到libs文件夹中，作为静态包使用。

##### @iconloaderlib: 
```
// AOSP/android-11/frameworks/libs/systemui/iconloaderlib
include ':iconloaderlib' //settings.gradle
```
![avatar](images/iconloaderlib.png)


##### @WifiTrackerLib: 
```
// AOSP/android-11/frameworks/opt/net/wifi/libs/WifiTrackerLib
include ':WifiTrackerLib' //settings.gradle
```
![avatar](images/WifiTrackerLib.png)


##### @SettingsLib: 
```
// AOSP/android-11/frameworks/base/packages/SettingsLib
// settings.gradle
include ':SettingsLib'
include 'SettingsLib:Tile'
include 'SettingsLib:AdaptiveIcon'
include 'SettingsLib:RestrictedLockUtils'
include 'SettingsLib:HelpUtils'
include 'SettingsLib:SettingsTheme'
include 'SettingsLib:AppPreference'
include 'SettingsLib:SearchWidget'
include 'SettingsLib:SettingsSpinner'
include 'SettingsLib:LayoutPreference'
include 'SettingsLib:ActionButtonsPreference'
include 'SettingsLib:EntityHeaderWidgets'
include 'SettingsLib:BarChartPreference'
include 'SettingsLib:ProgressBar'
include 'SettingsLib:RadioButtonPreference'
include 'SettingsLib:DisplayDensityUtils'
include 'SettingsLib:Utils'
include 'SettingsLib:ActionBarShadow'
```
![avatar](images/SettingsLib.png)

## 生成platform.keystore默认签名

在AOSP/android-11/build/target/product/security路径下找到签名证书，并使用 [keytool-importkeypair](https://github.com/getfatday/keytool-importkeypair) 生成keystore,
执行如下命令：  

```
./keytool-importkeypair -k platform.keystore -p 123456 -pk8 platform.pk8 -cert platform.x509.pem -alias platform
```

并将以下代码添加到gradle配置中：

```
    signingConfigs {
        platform {
            storeFile file("platform.keystore")
            storePassword '123456'
            keyAlias 'platform'
            keyPassword '123456'
        }
    }

    buildTypes {
        release {
            debuggable false
            minifyEnabled false
            signingConfig signingConfigs.platform
        }

        debug {
            debuggable true
            minifyEnabled false
            signingConfig signingConfigs.platform
        }
    }
```