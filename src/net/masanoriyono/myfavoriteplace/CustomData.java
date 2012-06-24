package net.masanoriyono.myfavoriteplace;

import com.google.android.maps.GeoPoint;
/**
 * @author Masanori YONO
 * ListViewに表示する内容を保持するクラス。
 * エリア名称
 * 登録されている地図名
 * 目的地エリアの頂点情報
 * 現在地から一番近い頂点
 * 現在地
 * Googleでルート検索した結果の所要時間と距離。（xxkm xx分）の形
 * ルート検索した結果の距離。
 */
public class CustomData {
	private int id;
    private String place_name;
    private GeoPoint current_pin;
    private String area_information;
    private String route_guide;
    private long distance;
    
   
    public CustomData(int t_id,String place_name,GeoPoint current_pin,String information) {
        this.id = t_id;
        this.place_name = place_name;

        
        this.current_pin = current_pin;
        this.area_information = information;
        
    }
    
    /**
     * 外部クラスでルート情報を検索してセットする場合。
     * @param navi：所要時間と距離（xxkm xx分）の形。
     * @param distance：距離
     */
    public void setRouteInfo(String navi,long distance) {
    	this.route_guide = navi;
    	this.distance = distance;
    }
    
    public GeoPoint getCurrentPoint() {
		return this.current_pin;
	}

    public String getRouteInfo() {
		return this.route_guide;
	}
    
    public int get_id() {
        return this.id;
    }
    
    public String getMapName() {
        return this.place_name;
    }

    public long getDistance() {
        return this.distance;
    }
    
    public String getAreaInfo() {
		return this.area_information;
	}

	
}
