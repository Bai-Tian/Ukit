package cat.nyaa.ukit.shop;

import land.melon.lab.simplelanguageloader.components.Text;

public class ShopLang {
    public Text successToCreateShopForBuy = Text.of("{player}的购买商店创建成功:{item}单价{price}卷");
    public Text successToCreateShopForSell = Text.of("{player}的收购商店创建成功:{item}单价{price}卷");
    public Text fail = Text.of("商店操作失败，原因:{reason}");
    public Text exception = Text.of("服务器内部错误");
}
