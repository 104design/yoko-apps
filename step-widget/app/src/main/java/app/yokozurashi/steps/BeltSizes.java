package app.yokozurashi.steps;

/** 流れる文字のベルトの幅（dp）とレイアウト。tools/make_belts.py が作る。直接書き換えない。 */
class BeltSizes {
    static final int[] WIDTHS = {
            300, 320, 340, 360, 380, 400, 420, 440, 460, 480,
            500, 520, 540, 560, 580, 600, 620, 640, 660, 680,
            700, 720, 740, 760, 780, 800, 820, 840, 860, 880,
            900, 920, 940, 960, 980, 1000, 1020, 1040, 1060, 1080,
            1100, 1120, 1140, 1160, 1180, 1200, 1220, 1240, 1260, 1280,
            1300,
    };
    static final int[] LAYOUTS = {
            R.layout.belt_300, R.layout.belt_320, R.layout.belt_340, R.layout.belt_360, R.layout.belt_380,
            R.layout.belt_400, R.layout.belt_420, R.layout.belt_440, R.layout.belt_460, R.layout.belt_480,
            R.layout.belt_500, R.layout.belt_520, R.layout.belt_540, R.layout.belt_560, R.layout.belt_580,
            R.layout.belt_600, R.layout.belt_620, R.layout.belt_640, R.layout.belt_660, R.layout.belt_680,
            R.layout.belt_700, R.layout.belt_720, R.layout.belt_740, R.layout.belt_760, R.layout.belt_780,
            R.layout.belt_800, R.layout.belt_820, R.layout.belt_840, R.layout.belt_860, R.layout.belt_880,
            R.layout.belt_900, R.layout.belt_920, R.layout.belt_940, R.layout.belt_960, R.layout.belt_980,
            R.layout.belt_1000, R.layout.belt_1020, R.layout.belt_1040, R.layout.belt_1060, R.layout.belt_1080,
            R.layout.belt_1100, R.layout.belt_1120, R.layout.belt_1140, R.layout.belt_1160, R.layout.belt_1180,
            R.layout.belt_1200, R.layout.belt_1220, R.layout.belt_1240, R.layout.belt_1260, R.layout.belt_1280,
            R.layout.belt_1300,
    };
}
