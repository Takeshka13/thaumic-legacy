package dev.thaumiclegacy.client;

import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import net.minecraft.client.gui.GuiGraphics;

final class AspectGlyphs {
	private static final int BADGE = 0xFF352E37;
	private static final int UNKNOWN_COLOR = 0xFFD0BCC1;
	private static final String UNKNOWN = "08142241221408";

	// Original sigils, encoded as seven rows of seven pixels each in glyph index order.
	private static final String[] GLYPHS = {
		"1c0238403c021c", "0814222a51423c", "081a2a2d514a3c", "081c3e6b3e1c08",
		"7741491c494177", "6132082c126443", "040A1428502000", "1008102854423C",
		"4122147F142241", "08080412092418", "08183C7F3C1808", "3E41495D49413E",
		"78101814121200", "00552255225500", "3E202C2C20007F", "3E08081C08083E",
		"1C2222227F413E", "20402004020102", "7F41494949417F", "001C2220280810",
		"4114227F221441", "003C421A22403C", "010204080C1C3C", "1C222208081408",
		"22493608364922", "0808082A1C0808", "3E49554955493E", "001C36497F0808",
		"0808141C08087F", "483010000C0641", "08081C222A221C", "083E082A081C08",
		"4141221C08087F", "1E291E083C4A3C", "08142A492A1408", "081C3641637700",
		"1C222A2A363E00", "7E020E020E027E", "1010100E001C00", "3E41415F45413E",
		"081C082A490808", "0608127F241830", "0002466C300000", "081C0849493E08",
		"081422417F2214", "081422417F1414", "22495563554922", "000814227F0022",
		"102e412a320c10", "3e49221c142a49", "552a4108412a55", "4464191f196444"
	};

	private AspectGlyphs() {}

	static void draw(GuiGraphics graphics, AspectView aspect, int x, int y) {
		int glyph = aspect.glyph();
		boolean known = !aspect.id().isEmpty() && glyph >= 0 && glyph < GLYPHS.length;
		String pattern = known ? GLYPHS[glyph] : UNKNOWN;
		int ink = known ? 0xFF000000 | aspect.color() : UNKNOWN_COLOR;
		graphics.fill(x, y, x + 11, y + 11, BADGE);
		for (int row = 0; row < 7; row++) {
			int pixels = Integer.parseInt(pattern, row * 2, row * 2 + 2, 16);
			for (int column = 0; column < 7; column++) {
				if ((pixels & (1 << (6 - column))) != 0) {
					int pixelX = x + 2 + column;
					int pixelY = y + 2 + row;
					graphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, ink);
				}
			}
		}
	}
}
