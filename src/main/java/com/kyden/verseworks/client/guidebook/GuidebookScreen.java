package com.kyden.verseworks.client.guidebook;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager.GuidebookBookDefinition;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager.GuidebookChapterDefinition;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager.GuidebookPageDefinition;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager.GuidebookPageType;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager.IconDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GuidebookScreen extends Screen {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "textures/gui/guidebook/frame.png");
    private static final ResourceLocation PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "textures/gui/guidebook/page_background.png");
    private static final int FRAME_TEXTURE_WIDTH = 752;
    private static final int FRAME_TEXTURE_HEIGHT = 420;
    private static final int PAGE_TEXTURE_SOURCE_WIDTH = 408;
    private static final int PAGE_TEXTURE_SOURCE_HEIGHT = 332;
    private static final int WIDTH = 376;
    private static final int HEIGHT = 210;
    private static final int SIDEBAR_WIDTH = 132;
    private static final int PAGE_X = 150;
    private static final int PAGE_WIDTH = 208;
    private static final int PAGE_HEIGHT = 178;
    private static final int PAGE_TEXTURE_WIDTH = 204;
    private static final int PAGE_TEXTURE_HEIGHT = 166;
    private static final int BODY_WRAP = 190;
    private static final int BODY_LINE_HEIGHT = 10;
    private static final int RITUAL_NOTE_WRAP = 68;
    private static final int COLOR_TEXT = 0x1F1A22;
    private static final int COLOR_TEXT_DIM = 0x3B3540;
    private static final int COLOR_TEXT_MUTED = 0x56505B;
    private static final int COLOR_TEXT_DARK = 0x4A434F;

    private final GuidebookBookDefinition book = GuidebookContentManager.activeBook();
    private final List<Button> chapterButtons = new ArrayList<>();
    private int leftPos;
    private int topPos;
    private int chapterIndex;
    private int pageIndex;
    private int subPageIndex;

    public GuidebookScreen() {
        super(Component.translatable("guidebook.verseworks.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - HEIGHT) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        this.chapterButtons.clear();
        int buttonY = this.topPos + 26;
        for (int index = 0; index < this.book.chapters().size(); index++) {
            final int chapter = index;
            GuidebookChapterDefinition definition = this.book.chapters().get(index);
            Button button = Button.builder(Component.translatable(definition.titleKey()), ignored -> switchChapter(chapter))
                .bounds(this.leftPos + 24, buttonY, 104, 18)
                .build();
            buttonY += 20;
            this.chapterButtons.add(button);
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> previousPage()).bounds(this.leftPos + PAGE_X + 4, this.topPos + 184, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> nextPage()).bounds(this.leftPos + PAGE_X + 26, this.topPos + 184, 18, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("guidebook.verseworks.home"), ignored -> goHome()).bounds(this.leftPos + PAGE_X + 128, this.topPos + 184, 58, 18).build());
    }

    private void switchChapter(int chapter) {
        this.chapterIndex = Mth.clamp(chapter, 0, this.book.chapters().size() - 1);
        this.pageIndex = 0;
        this.subPageIndex = 0;
    }

    private void goHome() {
        this.chapterIndex = 0;
        this.pageIndex = 0;
        this.subPageIndex = 0;
    }

    private void previousPage() {
        if (this.subPageIndex > 0) {
            this.subPageIndex--;
        } else if (this.pageIndex > 0) {
            this.pageIndex--;
            this.subPageIndex = Math.max(0, currentPageSegmentCount() - 1);
        } else if (this.chapterIndex > 0) {
            this.chapterIndex--;
            this.pageIndex = Math.max(0, currentChapter().pages().size() - 1);
            this.subPageIndex = Math.max(0, currentPageSegmentCount() - 1);
        }
    }

    private void nextPage() {
        if (this.subPageIndex + 1 < currentPageSegmentCount()) {
            this.subPageIndex++;
        } else if (this.pageIndex + 1 < currentChapter().pages().size()) {
            this.pageIndex++;
            this.subPageIndex = 0;
        } else if (this.chapterIndex + 1 < this.book.chapters().size()) {
            this.chapterIndex++;
            this.pageIndex = 0;
            this.subPageIndex = 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentPageSegmentCount() > 1 && scrollY != 0.0D) {
            if (scrollY < 0.0D) {
                nextPage();
            } else {
                previousPage();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(FRAME_TEXTURE, this.leftPos, this.topPos, 0, 0, WIDTH, HEIGHT, FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT);
        guiGraphics.blit(PAGE_TEXTURE, this.leftPos + PAGE_X, this.topPos + 14, 0, 0, PAGE_TEXTURE_WIDTH, PAGE_TEXTURE_HEIGHT, PAGE_TEXTURE_SOURCE_WIDTH, PAGE_TEXTURE_SOURCE_HEIGHT);
        renderSidebar(guiGraphics);
        renderCurrentPage(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, Component.translatable(this.book.titleKey()), this.leftPos + 10, this.topPos + 10, COLOR_TEXT, false);
        guiGraphics.drawWordWrap(this.font, Component.translatable(this.book.subtitleKey()), this.leftPos + 24, this.topPos + 186, 104, COLOR_TEXT_MUTED);
        for (int index = 0; index < this.book.chapters().size(); index++) {
            GuidebookChapterDefinition chapter = this.book.chapters().get(index);
            int y = this.topPos + 26 + index * 20;
            renderIcon(guiGraphics, chapter.icon(), this.leftPos + 7, y + 1);
        }
    }

    private void renderCurrentPage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GuidebookPageDefinition page = currentPage();
        boolean continuation = this.subPageIndex > 0;
        int pageLeft = this.leftPos + PAGE_X + 8;
        int pageTop = this.topPos + 22;
        guiGraphics.drawString(this.font, Component.translatable(currentChapter().titleKey()), pageLeft, pageTop, COLOR_TEXT_DIM, false);
        guiGraphics.drawString(this.font, Component.translatable(page.titleKey()), pageLeft, pageTop + 14, COLOR_TEXT, false);
        switch (page.type()) {
            case TEXT -> renderBody(guiGraphics, page, pageLeft, pageTop + 30, 11, 11);
            case IMAGE -> {
                if (!continuation) {
                    renderImagePanel(guiGraphics, page.image(), pageLeft, pageTop + 30, 184, 96);
                    renderBody(guiGraphics, page, pageLeft, pageTop + 132, 2, 11);
                } else {
                    renderBody(guiGraphics, page, pageLeft, pageTop + 30, 2, 11);
                }
            }
            case ITEM_SPOTLIGHT -> {
                if (!continuation && page.icon() != null) {
                    renderIcon(guiGraphics, page.icon(), pageLeft, pageTop + 34);
                }
                if (!continuation && page.iconLabelKey() != null) {
                    guiGraphics.drawWordWrap(this.font, Component.translatable(page.iconLabelKey()), pageLeft + 22, pageTop + 34, 164, COLOR_TEXT_DIM);
                }
                renderBody(guiGraphics, page, pageLeft, continuation ? pageTop + 30 : pageTop + 58, 8, 11);
            }
            case RITUAL_LAYOUT -> {
                if (!continuation) {
                    renderRitual(guiGraphics, page, pageLeft, pageTop + 34);
                    renderBody(guiGraphics, page, pageLeft, pageTop + 122, 2, 11);
                } else {
                    renderBody(guiGraphics, page, pageLeft, pageTop + 30, 3, 11);
                }
            }
            case FEATURE_CALLOUT -> {
                if (!continuation && page.icon() != null) {
                    renderIcon(guiGraphics, page.icon(), pageLeft, pageTop + 34);
                }
                if (!continuation && page.image() != null) {
                    renderImagePanel(guiGraphics, page.image(), pageLeft + 32, pageTop + 30, 152, 76);
                    renderBody(guiGraphics, page, pageLeft, pageTop + 114, 3, 11);
                } else {
                    renderBody(guiGraphics, page, pageLeft, continuation ? pageTop + 30 : pageTop + 58, 8, 11);
                }
            }
            case LINK_LIST -> renderLinks(guiGraphics, page, pageLeft, pageTop + 34, mouseX, mouseY);
        }
        guiGraphics.drawString(this.font, Component.literal(displayedPageNumber() + "/" + totalDisplayPageCount()), this.leftPos + PAGE_X + 90, this.topPos + 189, COLOR_TEXT_DARK, false);
    }

    private void renderBody(GuiGraphics guiGraphics, GuidebookPageDefinition page, int x, int y, int firstCapacity, int continuationCapacity) {
        List<List<FormattedCharSequence>> segments = segmentBodyLines(page, firstCapacity, continuationCapacity);
        int segmentIndex = Mth.clamp(this.subPageIndex, 0, Math.max(0, segments.size() - 1));
        int drawY = y;
        for (FormattedCharSequence line : segments.get(segmentIndex)) {
            guiGraphics.drawString(this.font, line, x, drawY, COLOR_TEXT_DIM, false);
            drawY += BODY_LINE_HEIGHT;
        }
    }

    private void renderImagePanel(GuiGraphics guiGraphics, String imagePath, int x, int y, int width, int height) {
        if (imagePath == null) {
            drawMissingImage(guiGraphics, x, y, width, height, "guidebook.image.missing");
            return;
        }
        ResourceLocation texture = ResourceLocation.parse(imagePath);
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
            drawMissingImage(guiGraphics, x, y, width, height, texture.toString());
            return;
        }
        guiGraphics.blit(texture, x, y, 0, 0, width, height, width, height);
    }

    private void drawMissingImage(GuiGraphics guiGraphics, int x, int y, int width, int height, String label) {
        guiGraphics.fill(x, y, x + width, y + height, 0xAA1E1625);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA3A2A49);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2, y + height / 2 - 4, COLOR_TEXT);
    }

    private void renderRitual(GuiGraphics guiGraphics, GuidebookPageDefinition page, int x, int y) {
        if (page.icon() != null) {
            renderIcon(guiGraphics, page.icon(), x + 76, y + 20);
        }
        int[][] offsets = {{0, 20}, {38, 0}, {76, 20}, {38, 40}};
        for (int index = 0; index < Math.min(offsets.length, page.ingredients().size()); index++) {
            renderIcon(guiGraphics, page.ingredients().get(index), x + offsets[index][0], y + offsets[index][1]);
        }
        int noteY = y + 8;
        for (String noteKey : page.notes()) {
            Component note = Component.translatable(noteKey);
            guiGraphics.drawWordWrap(this.font, note, x + 118, noteY, RITUAL_NOTE_WRAP, COLOR_TEXT_MUTED);
            noteY += this.font.wordWrapHeight(note, RITUAL_NOTE_WRAP) + 8;
        }
    }

    private void renderLinks(GuiGraphics guiGraphics, GuidebookPageDefinition page, int x, int y, int mouseX, int mouseY) {
        renderBody(guiGraphics, page, x, y, 5, 11);
        int linkY = y + 52;
        for (String chapterId : page.linkChapterIds()) {
            Component label = this.book.chapter(chapterId)
                .map(chapter -> Component.translatable(chapter.titleKey()))
                .orElse(Component.literal(chapterId));
            guiGraphics.drawString(this.font, label, x, linkY, COLOR_TEXT, false);
            linkY += 14;
        }
    }

    private void renderIcon(GuiGraphics guiGraphics, IconDefinition icon, int x, int y) {
        if (icon == null) {
            return;
        }
        if ("texture".equals(icon.type())) {
            ResourceLocation texture = icon.resolveTexture();
            guiGraphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
            return;
        }
        guiGraphics.renderItem(new ItemStack(icon.resolveItem()), x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GuidebookPageDefinition page = currentPage();
        if (page.type() == GuidebookPageType.LINK_LIST) {
            int x = this.leftPos + PAGE_X + 8;
            int linkY = this.topPos + 22 + 34 + 52;
            for (String chapterId : page.linkChapterIds()) {
                int width = this.font.width(Component.translatable(this.book.chapter(chapterId).map(GuidebookChapterDefinition::titleKey).orElse("guidebook.chapter.missing.title")));
                if (mouseX >= x && mouseX <= x + width && mouseY >= linkY && mouseY <= linkY + 10) {
                    this.book.chapter(chapterId).ifPresent(chapter -> switchChapter(this.book.chapters().indexOf(chapter)));
                    return true;
                }
                linkY += 14;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private GuidebookChapterDefinition currentChapter() {
        return this.book.chapters().get(Mth.clamp(this.chapterIndex, 0, this.book.chapters().size() - 1));
    }

    private GuidebookPageDefinition currentPage() {
        List<GuidebookPageDefinition> pages = currentChapter().pages();
        return pages.get(Mth.clamp(this.pageIndex, 0, pages.size() - 1));
    }

    private int currentPageSegmentCount() {
        GuidebookPageDefinition page = currentPage();
        return switch (page.type()) {
            case TEXT -> segmentBodyLines(page, 11, 11).size();
            case IMAGE -> segmentBodyLines(page, 2, 11).size();
            case ITEM_SPOTLIGHT, FEATURE_CALLOUT -> segmentBodyLines(page, 8, 11).size();
            case RITUAL_LAYOUT -> segmentBodyLines(page, 2, 11).size();
            case LINK_LIST -> 1;
        };
    }

    private int displayedPageNumber() {
        int pageNumber = 1;
        for (int index = 0; index < this.pageIndex; index++) {
            pageNumber += pageSegmentCount(currentChapter().pages().get(index));
        }
        return pageNumber + this.subPageIndex;
    }

    private int totalDisplayPageCount() {
        int total = 0;
        for (GuidebookPageDefinition page : currentChapter().pages()) {
            total += pageSegmentCount(page);
        }
        return Math.max(1, total);
    }

    private int pageSegmentCount(GuidebookPageDefinition page) {
        return switch (page.type()) {
            case TEXT -> segmentBodyLines(page, 11, 11).size();
            case IMAGE -> segmentBodyLines(page, 2, 11).size();
            case ITEM_SPOTLIGHT, FEATURE_CALLOUT -> segmentBodyLines(page, 8, 11).size();
            case RITUAL_LAYOUT -> segmentBodyLines(page, 2, 11).size();
            case LINK_LIST -> 1;
        };
    }

    private List<List<FormattedCharSequence>> segmentBodyLines(GuidebookPageDefinition page, int firstCapacity, int continuationCapacity) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int index = 0; index < page.bodyKeys().size(); index++) {
            lines.addAll(this.font.split(Component.translatable(page.bodyKeys().get(index)), BODY_WRAP));
            if (index + 1 < page.bodyKeys().size()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
        }

        if (lines.isEmpty()) {
            List<List<FormattedCharSequence>> empty = new ArrayList<>();
            empty.add(List.of());
            return empty;
        }

        List<List<FormattedCharSequence>> segments = new ArrayList<>();
        int index = 0;
        int capacity = Math.max(1, firstCapacity);
        while (index < lines.size()) {
            int end = Math.min(lines.size(), index + capacity);
            segments.add(new ArrayList<>(lines.subList(index, end)));
            index = end;
            capacity = Math.max(1, continuationCapacity);
        }
        return segments;
    }
}
