package com.xfjy.server.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HtmlSanitizer} 白名单净化测试：用真实 XSS 载荷验证危险标签/属性/协议被剥离，
 * 同时保证编辑器产出的合法富文本不被误伤。断言只校验关键安全性，不依赖 jsoup 的精确格式。
 */
class HtmlSanitizerTest {

    @Test
    void shouldStripScriptTagButKeepSafeText() {
        String result = HtmlSanitizer.clean("<p>正常文本</p><script>alert('xss')</script>");

        assertFalse(result.toLowerCase().contains("<script"), "script 标签必须被移除");
        assertFalse(result.contains("alert"), "script 内的脚本内容不得残留");
        assertTrue(result.contains("正常文本"), "安全文本必须保留");
    }

    @Test
    void shouldRemoveImgWithOnerrorEvent() {
        String result = HtmlSanitizer.clean("<img src=\"x\" onerror=\"alert(1)\">");

        assertFalse(result.toLowerCase().contains("<img"), "img 不在白名单，标签应整体移除");
        assertFalse(result.toLowerCase().contains("onerror"), "事件处理器必须被剥离");
    }

    @Test
    void shouldStripJavascriptProtocolButKeepAnchorText() {
        String result = HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">点我领奖</a>");

        assertFalse(result.toLowerCase().contains("javascript:"), "javascript: 协议必须被清除");
        assertTrue(result.contains("点我领奖"), "链接文本应保留");
    }

    @Test
    void shouldStripInlineEventHandlerOnWhitelistedTag() {
        String result = HtmlSanitizer.clean("<p onclick=\"steal()\" class=\"x\">段落</p>");

        assertTrue(result.contains("段落"), "白名单标签 p 与文本应保留");
        assertFalse(result.toLowerCase().contains("onclick"), "白名单标签上的事件属性必须剥离");
        assertFalse(result.contains("class"), "未放行的属性必须剥离");
    }

    @Test
    void shouldKeepLegitimateRichTextAndHardenLinks() {
        String html = "<b>粗体</b><i>斜体</i><ul><li>列表项</li></ul>"
                + "<a href=\"https://example.com/news\">安全链接</a>";
        String result = HtmlSanitizer.clean(html);

        assertTrue(result.contains("<b>粗体</b>"), "粗体标签应保留");
        assertTrue(result.contains("<i>斜体</i>"), "斜体标签应保留");
        assertTrue(result.contains("<li>列表项</li>"), "列表标签应保留");
        assertTrue(result.contains("https://example.com/news"), "https 链接应保留");
        assertTrue(result.contains("安全链接"), "链接文本应保留");
        // 超链接统一加固：新窗口 + 防 reverse tabnabbing
        assertTrue(result.contains("target=\"_blank\""), "链接应强制 target=_blank");
        assertTrue(result.contains("noopener"), "链接应带 nonoopener 防 tabnabbing");
    }

    @Test
    void shouldReturnNullWhenInputIsNull() {
        // 保留 null 语义：updateNotice 依赖 content != null 判断是否更新该字段
        assertNull(HtmlSanitizer.clean(null), "null 入参必须原样返回 null");
    }
}
