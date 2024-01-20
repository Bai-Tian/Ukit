package cat.nyaa.ukit.elytra;

import land.melon.lab.simplelanguageloader.components.Text;

public class ElytraLang {
    public Text help = Text.of(
            "&7Usage of /ukit elytra:",
            "&7    &6/ukit elytra speedometer [enable|disable|toggle]: enable/disable/toggle whether to display speedometer.",
            "&7    Note: empty parameter (/ukit elytra speedometer) will toggle by default."
    );

    public Text speedometerEnabled = new Text("&7Speedometer enabled!");
    public Text speedometerDisabled = new Text("&7Speedometer disabled!");
}
