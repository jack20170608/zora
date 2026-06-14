package top.ilovemyhome.zora.poc.tui.claude;

/** Represents one selectable option in the interactive configuration menu. */
record ConfigMenuOption(String selector, String label, String value) {

    ConfigMenuOption {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector must not be blank");
        }
        label = label == null ? "" : label;
        value = value == null ? "" : value;
    }
}
