package com.siren.filter;

import java.io.File;

/**
 * 主程序
 * Created by Siren on 2022/4/7.
 */
public class Main {

    /**
     * 执行过滤任务
     */
    public static void main(String[] args) {
        filterResource();
        replaceContent();
    }

    /**
     * 过滤资源文件
     */
    private static void filterResource() {
        String[] arr = new String[]{
                "res",
                "res-product",
        };

        for (String name : arr) {
            String path = System.getProperty("user.dir") + File.separator + name;
            // 可选项：清除多余的国际化语言，可提高编译效率
            FilterMultiLang.filter(path);
            // 必选项：清除string里面的product属性，如tablet、device等，因为AS无法识别该属性，会编译不通过
            FilterAttribute.filter(path);
        }
    }

    /**
     * 暴力替换内容，解决私有资源引用失败以及语法不支持等
     */
    private static void replaceContent() {
        FileContentReplacer.replaceInPath("res-keyguard/values/styles.xml", "<item name=\"android:textColor\">?androidprv:attr/materialColorOnSurface</item>", "<item name=\"android:textColor\">#00ffffff</item>");
        FileContentReplacer.replaceInPath("res-keyguard/values/styles.xml", "<item name=\"android:textColor\">?androidprv:attr/materialColorOnSurfaceVariant</item>", "<item name=\"android:textColor\">#00ffffff</item>");
        FileContentReplacer.replaceInPath("res-keyguard/values/styles.xml", "<item name=\"android:textColor\">?androidprv:attr/materialColorOnTertiaryFixed</item>", "<item name=\"android:textColor\">#00ffffff</item>");

        FileContentReplacer.replaceInPath("res/layout/hybrid_notification.xml", "@*android:dimen/notification_content_margin_start", "16dp");
        FileContentReplacer.replaceInPath("res/layout/people_space_activity_no_conversations.xml", "@*android:dimen/notification_content_margin_start", "?androidprv:attr/textColorOnAccent");
        FileContentReplacer.replaceInPath("res/layout/people_tile_large_with_content.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/layout/people_tile_medium_with_content.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/layout/people_tile_small.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/layout/people_tile_small_horizontal.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/layout/screen_share_dialog_spinner_item_text.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");

        // 替换引用不到的@*android:id/action_bar私有属性
        FileContentReplacer.replaceInPath("res/layout/qs_customize_panel_content.xml", "@*android:id/action_bar", "@+id/action_bar");
        FileContentReplacer.replaceInPath("src/com/android/systemui/qs/customize/QSCustomizer.java", "com.android.internal.R.id.action_bar", "R.id.action_bar");
        FileContentReplacer.replaceInPath("src/com/android/systemui/qs/customize/QSCustomizerController.java", "com.android.internal.R.id.action_bar", "R.id.action_bar");

        FileContentReplacer.replaceInPath("res/values/colors.xml", "<color name=\"ksh_key_item_background\">?androidprv:attr/materialColorSurfaceContainerHighest</color>", "<color name=\"ksh_key_item_background\">#00ffffff</color>");
        FileContentReplacer.replaceInPath("res/values/dimens.xml", "<dimen name=\"status_bar_icon_size_sp\">@*android:dimen/status_bar_icon_size_sp</dimen>", "<dimen name=\"status_bar_icon_size_sp\">22sp</dimen>");
        FileContentReplacer.replaceInPath("res/values/dimens.xml", "<dimen name=\"group_overflow_number_size\">@*android:dimen/notification_text_size</dimen>", "<dimen name=\"group_overflow_number_size\">14sp</dimen>");
        FileContentReplacer.replaceInPath("res/values/dimens.xml", "<dimen name=\"group_overflow_number_padding\">@*android:dimen/notification_content_margin_end", "<dimen name=\"group_overflow_number_padding\">16dp");
        FileContentReplacer.replaceInPath("res/values/dimens.xml", "<dimen name=\"notification_min_height\">@*android:dimen/notification_min_height</dimen>", "<dimen name=\"notification_min_height\">112dp</dimen>");

        FileContentReplacer.replaceInPath("res/values/styles.xml", "<item name=\"*android:lockPatternStyle\">@style/LockPatternViewStyle</item>", "<item name=\"*android:lockPatternStyle\">@null</item>");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<style name=\"AlertDialogStyle\" parent=\"@androidprv:style/AlertDialog.DeviceDefault\">", "<style name=\"AlertDialogStyle\" parent=\"Theme.AppCompat.Dialog.Alert\">");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<style name=\"ScrollableAlertDialogStyle\" parent=\"@androidprv:style/AlertDialog.DeviceDefault\">", "<style name=\"ScrollableAlertDialogStyle\" parent=\"Theme.AppCompat.Dialog.Alert\">");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<style name=\"ButtonBarStyle\" parent=\"@androidprv:style/DeviceDefault.ButtonBar.AlertDialog\">", "<style name=\"ButtonBarStyle\" parent=\"Widget.AppCompat.ButtonBar.AlertDialog\">");
        FileContentReplacer.replaceInPath("res/values-television/styles.xml", "<item name=\"androidprv:textColorOnAccent\">@color/tv_volume_dialog_accent</item>", "");

        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorOnSurfaceVariant", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorOnSurface", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorSurfaceBright", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/colorAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/textColorPrimary", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorSurfaceContainerHighest", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorSurfaceContainerHigh", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorSurfaceContainer", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorPrimary", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorTertiary", "#00ffffff");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "?androidprv:attr/materialColorOutline", "#00ffffff");
        FileContentReplacer.replaceInPath("Shell/res/values/styles.xml", "?androidprv:attr/materialColorOnSurface", "#00ffffff");
        FileContentReplacer.replaceInPath("SettingsLib/res/values/styles.xml", "?androidprv:attr/textColorOnAccent", "#00ffffff");

        // 不支持扩展函数，换了一种写法
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/NoteTaskModule.kt", "fun NoteTaskControllerUpdateService.bindNoteTaskControllerUpdateService(): Service", "fun bindNoteTaskControllerUpdateService(service: NoteTaskControllerUpdateService): Service");
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/NoteTaskModule.kt", "fun NoteTaskBubblesController.NoteTaskBubblesService.bindNoteTaskBubblesService(): Service", "fun bindNoteTaskBubblesService(service: NoteTaskBubblesController.NoteTaskBubblesService): Service");
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/NoteTaskModule.kt", "fun LaunchNoteTaskActivity.bindNoteTaskLauncherActivity(): Activity", "fun bindNoteTaskLauncherActivity(activity: LaunchNoteTaskActivity): Activity");
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/NoteTaskModule.kt", "fun LaunchNotesRoleSettingsTrampolineActivity.bindLaunchNotesRoleSettingsTrampolineActivity():", "fun bindLaunchNotesRoleSettingsTrampolineActivity(activity: LaunchNotesRoleSettingsTrampolineActivity):");
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/NoteTaskModule.kt", "fun CreateNoteTaskShortcutActivity.bindNoteTaskShortcutActivity(): Activity", "fun bindNoteTaskShortcutActivity(activity: CreateNoteTaskShortcutActivity): Activity");
        FileContentReplacer.replaceInPath("src/com/android/systemui/notetask/quickaffordance/NoteTaskQuickAffordanceModule.kt", "fun NoteTaskQuickAffordanceConfig.bindNoteTaskQuickAffordance(): KeyguardQuickAffordanceConfig", "fun bindNoteTaskQuickAffordance(config: NoteTaskQuickAffordanceConfig): KeyguardQuickAffordanceConfig");
    }
}
