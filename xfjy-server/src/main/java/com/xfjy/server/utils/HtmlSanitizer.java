package com.xfjy.server.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 富文本净化工具（存储型 XSS 防护）。
 *
 * 后台公告等富文本经 contenteditable 编辑器产出 HTML 后入库，前端 v-html 渲染会执行
 * 其中脚本。统一在写入方入库前用 jsoup 白名单清洗：仅保留编辑器实际产出的排版/链接
 * 标签与 href 属性，剥离 script、事件属性（onerror/onclick 等）及 javascript: 协议；
 * 恶意标签整体移除，但标签内的安全文本保留。
 */
public final class HtmlSanitizer {

    /**
     * 富文本白名单：对齐公告编辑器能力（粗体/斜体/下划线/段落/有序无序列表/超链接）。
     * 静态共享、构建后只读，Cleaner 仅读取不修改，线程安全。
     */
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("b", "strong", "i", "em", "u", "br", "p", "div",
                    "ul", "ol", "li", "a")
            // 超链接仅保留 href，且只允许 http/https/mailto，拒绝 javascript:/data: 等危险协议
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto")
            // 统一新窗口打开并防 reverse tabnabbing
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow");

    private HtmlSanitizer() {
    }

    /**
     * 清洗富文本 HTML，剥离白名单外的标签与属性。
     *
     * @param html 原始 HTML，可为 null
     * @return 净化后的安全 HTML；入参为 null 时原样返回 null（保留更新逻辑的 null 语义）
     */
    public static String clean(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
