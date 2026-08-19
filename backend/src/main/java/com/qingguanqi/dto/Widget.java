package com.qingguanqi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Widget {
    private String type;       // option_list | form_card | info_card | nav_card
    private String title;
    private String description;

    // option_list
    private List<WidgetOption> options;

    // form_card
    private List<WidgetField> fields;
    private String submitLabel;

    // info_card: rows = fields (as key-value), optional action buttons
    private List<WidgetField> rows;
    private List<WidgetAction> actions;

    // nav_card
    private String route;
    private String routeLabel;

    // ---------- sub-types ----------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WidgetOption {
        private String label;
        private String value;
        private String description;
        private boolean disabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WidgetField {
        private String label;      // natural language label
        private String key;        // internal key
        private String type;       // text | number | select | datetime | readonly
        private String value;      // default / current value
        private String placeholder;
        private String hint;       // format hint or example
        private List<WidgetOption> options;  // for select type
        private boolean required;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WidgetAction {
        private String label;
        private String action;     // identifier
        private String value;      // payload
        private String style;      // primary | danger | default
    }
}
