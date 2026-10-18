package com.gds.seed;

import java.util.List;

/**
 * Static master data the mock suppliers are generated from. Airports are real IATA codes;
 * airlines, hotel chains and car vendors are fictional so the data is clearly a simulation.
 */
final class ReferenceData {

    private ReferenceData() {
    }

    record AirportSeed(String code, String name, String city, String country, String timezone, double lat, double lon,
                       double costFactor) {
    }

    record AirlineSeed(String code, String name, String country, String hub, List<String> destinations) {
    }

    record ChainSeed(String code, String brand, int minStars, int maxStars) {
    }

    record CarSeed(String sipp, String model, double baseRate) {
    }

    static final List<AirportSeed> AIRPORTS = List.of(
            new AirportSeed("JFK", "John F. Kennedy Intl", "New York", "US", "America/New_York", 40.6413, -73.7781, 1.4),
            new AirportSeed("LAX", "Los Angeles Intl", "Los Angeles", "US", "America/Los_Angeles", 33.9416, -118.4085, 1.3),
            new AirportSeed("ORD", "O'Hare Intl", "Chicago", "US", "America/Chicago", 41.9742, -87.9073, 1.2),
            new AirportSeed("ATL", "Hartsfield-Jackson Atlanta Intl", "Atlanta", "US", "America/New_York", 33.6407, -84.4277, 1.0),
            new AirportSeed("DFW", "Dallas/Fort Worth Intl", "Dallas", "US", "America/Chicago", 32.8998, -97.0403, 1.0),
            new AirportSeed("DEN", "Denver Intl", "Denver", "US", "America/Denver", 39.8561, -104.6737, 1.0),
            new AirportSeed("SFO", "San Francisco Intl", "San Francisco", "US", "America/Los_Angeles", 37.6213, -122.3790, 1.5),
            new AirportSeed("SEA", "Seattle-Tacoma Intl", "Seattle", "US", "America/Los_Angeles", 47.4502, -122.3088, 1.2),
            new AirportSeed("MIA", "Miami Intl", "Miami", "US", "America/New_York", 25.7959, -80.2870, 1.2),
            new AirportSeed("BOS", "Logan Intl", "Boston", "US", "America/New_York", 42.3656, -71.0096, 1.3),
            new AirportSeed("YYZ", "Toronto Pearson Intl", "Toronto", "CA", "America/Toronto", 43.6777, -79.6248, 1.1),
            new AirportSeed("YVR", "Vancouver Intl", "Vancouver", "CA", "America/Vancouver", 49.1967, -123.1815, 1.1),
            new AirportSeed("MEX", "Benito Juarez Intl", "Mexico City", "MX", "America/Mexico_City", 19.4361, -99.0719, 0.7),
            new AirportSeed("CUN", "Cancun Intl", "Cancun", "MX", "America/Cancun", 21.0365, -86.8771, 0.9),
            new AirportSeed("GDL", "Guadalajara Intl", "Guadalajara", "MX", "America/Mexico_City", 20.5218, -103.3112, 0.6),
            new AirportSeed("MTY", "Monterrey Intl", "Monterrey", "MX", "America/Monterrey", 25.7785, -100.1070, 0.7),
            new AirportSeed("BOG", "El Dorado Intl", "Bogota", "CO", "America/Bogota", 4.7016, -74.1469, 0.6),
            new AirportSeed("LIM", "Jorge Chavez Intl", "Lima", "PE", "America/Lima", -12.0219, -77.1143, 0.6),
            new AirportSeed("GRU", "Sao Paulo/Guarulhos Intl", "Sao Paulo", "BR", "America/Sao_Paulo", -23.4356, -46.4731, 0.8),
            new AirportSeed("EZE", "Ministro Pistarini Intl", "Buenos Aires", "AR", "America/Argentina/Buenos_Aires", -34.8222, -58.5358, 0.7),
            new AirportSeed("SCL", "Arturo Merino Benitez Intl", "Santiago", "CL", "America/Santiago", -33.3930, -70.7858, 0.7),
            new AirportSeed("LHR", "Heathrow", "London", "GB", "Europe/London", 51.4700, -0.4543, 1.6),
            new AirportSeed("CDG", "Charles de Gaulle", "Paris", "FR", "Europe/Paris", 49.0097, 2.5479, 1.5),
            new AirportSeed("FRA", "Frankfurt am Main", "Frankfurt", "DE", "Europe/Berlin", 50.0379, 8.5622, 1.2),
            new AirportSeed("AMS", "Schiphol", "Amsterdam", "NL", "Europe/Amsterdam", 52.3105, 4.7683, 1.4),
            new AirportSeed("MAD", "Adolfo Suarez Madrid-Barajas", "Madrid", "ES", "Europe/Madrid", 40.4983, -3.5676, 1.0),
            new AirportSeed("BCN", "Josep Tarradellas Barcelona-El Prat", "Barcelona", "ES", "Europe/Madrid", 41.2974, 2.0833, 1.1),
            new AirportSeed("FCO", "Leonardo da Vinci-Fiumicino", "Rome", "IT", "Europe/Rome", 41.8003, 12.2389, 1.1),
            new AirportSeed("MUC", "Munich", "Munich", "DE", "Europe/Berlin", 48.3538, 11.7861, 1.2),
            new AirportSeed("ZRH", "Zurich", "Zurich", "CH", "Europe/Zurich", 47.4582, 8.5555, 1.7),
            new AirportSeed("IST", "Istanbul", "Istanbul", "TR", "Europe/Istanbul", 41.2753, 28.7519, 0.8),
            new AirportSeed("DXB", "Dubai Intl", "Dubai", "AE", "Asia/Dubai", 25.2532, 55.3657, 1.3),
            new AirportSeed("DOH", "Hamad Intl", "Doha", "QA", "Asia/Qatar", 25.2731, 51.6081, 1.2),
            new AirportSeed("JNB", "O. R. Tambo Intl", "Johannesburg", "ZA", "Africa/Johannesburg", -26.1367, 28.2411, 0.7),
            new AirportSeed("DEL", "Indira Gandhi Intl", "Delhi", "IN", "Asia/Kolkata", 28.5562, 77.1000, 0.6),
            new AirportSeed("BOM", "Chhatrapati Shivaji Maharaj Intl", "Mumbai", "IN", "Asia/Kolkata", 19.0896, 72.8656, 0.7),
            new AirportSeed("SIN", "Changi", "Singapore", "SG", "Asia/Singapore", 1.3644, 103.9915, 1.3),
            new AirportSeed("HKG", "Hong Kong Intl", "Hong Kong", "HK", "Asia/Hong_Kong", 22.3080, 113.9185, 1.3),
            new AirportSeed("NRT", "Narita Intl", "Tokyo", "JP", "Asia/Tokyo", 35.7720, 140.3929, 1.3),
            new AirportSeed("ICN", "Incheon Intl", "Seoul", "KR", "Asia/Seoul", 37.4602, 126.4407, 1.1),
            new AirportSeed("PEK", "Beijing Capital Intl", "Beijing", "CN", "Asia/Shanghai", 40.0799, 116.6031, 1.0),
            new AirportSeed("BKK", "Suvarnabhumi", "Bangkok", "TH", "Asia/Bangkok", 13.6900, 100.7501, 0.7),
            new AirportSeed("SYD", "Kingsford Smith", "Sydney", "AU", "Australia/Sydney", -33.9399, 151.1753, 1.3),
            new AirportSeed("MEL", "Melbourne", "Melbourne", "AU", "Australia/Melbourne", -37.6690, 144.8410, 1.2));

    static final List<AirlineSeed> AIRLINES = List.of(
            new AirlineSeed("NA", "Northstar Airways", "US", "JFK",
                    List.of("LAX", "ORD", "ATL", "MIA", "BOS", "SFO", "LHR", "CDG", "MAD", "FCO", "MEX", "CUN", "GRU", "NRT", "DXB")),
            new AirlineSeed("PC", "Pacific Crest", "US", "LAX",
                    List.of("SFO", "SEA", "DEN", "DFW", "ORD", "JFK", "YVR", "MEX", "GDL", "NRT", "ICN", "SYD", "HKG")),
            new AirlineSeed("SB", "Sunbelt Air", "US", "ATL",
                    List.of("JFK", "MIA", "DFW", "ORD", "DEN", "BOS", "CUN", "MEX", "BOG", "LIM", "AMS", "CDG")),
            new AirlineSeed("PR", "Prairie Airlines", "US", "DFW",
                    List.of("DEN", "ORD", "LAX", "ATL", "SEA", "MTY", "MEX", "GDL", "CUN", "LHR")),
            new AirlineSeed("AQ", "Aurora Air", "CA", "YYZ",
                    List.of("YVR", "JFK", "ORD", "BOS", "MIA", "LHR", "CDG", "FRA", "CUN", "MEX")),
            new AirlineSeed("AZ", "Aztlan Airlines", "MX", "MEX",
                    List.of("CUN", "GDL", "MTY", "LAX", "JFK", "ORD", "DFW", "MIA", "BOG", "LIM", "MAD", "GRU")),
            new AirlineSeed("CN", "Condor Andino", "CO", "BOG",
                    List.of("LIM", "MEX", "MIA", "SCL", "GRU", "EZE", "MAD", "CUN", "JFK")),
            new AirlineSeed("SR", "Southern Cross Air", "BR", "GRU",
                    List.of("EZE", "SCL", "LIM", "BOG", "MIA", "JFK", "MAD", "JNB")),
            new AirlineSeed("BW", "Britannia Wings", "GB", "LHR",
                    List.of("JFK", "BOS", "MIA", "CDG", "AMS", "MAD", "FCO", "ZRH", "DXB", "DEL", "SIN", "JNB", "YYZ")),
            new AirlineSeed("EL", "Euroline", "DE", "FRA",
                    List.of("MUC", "CDG", "AMS", "MAD", "BCN", "FCO", "IST", "ZRH", "JFK", "ORD", "PEK", "DEL", "DXB")),
            new AirlineSeed("ML", "Mediterra", "ES", "MAD",
                    List.of("BCN", "CDG", "FCO", "LHR", "MUC", "MEX", "BOG", "EZE", "GRU", "LIM", "MIA")),
            new AirlineSeed("GS", "Gulfstar Airways", "AE", "DXB",
                    List.of("DOH", "LHR", "CDG", "FRA", "IST", "DEL", "BOM", "SIN", "BKK", "SYD", "JNB", "JFK")),
            new AirlineSeed("LT", "Lotus Air", "SG", "SIN",
                    List.of("HKG", "BKK", "NRT", "ICN", "SYD", "MEL", "DEL", "BOM", "DXB", "LHR", "PEK")),
            new AirlineSeed("SK", "Sakura Skyways", "JP", "NRT",
                    List.of("ICN", "PEK", "HKG", "SIN", "BKK", "LAX", "SFO", "SEA", "JFK", "SYD")),
            new AirlineSeed("KA", "Kangaroo Air", "AU", "SYD",
                    List.of("MEL", "SIN", "HKG", "NRT", "LAX", "SFO", "BKK")));

    static final List<ChainSeed> CHAINS = List.of(
            new ChainSeed("MG", "Meridian Grand", 4, 5),
            new ChainSeed("AZ", "Azure Suites", 4, 5),
            new ChainSeed("CB", "Cobalt Inn", 2, 3),
            new ChainSeed("HR", "Harbor Residences", 3, 4),
            new ChainSeed("SL", "Solstice Hotels", 3, 4),
            new ChainSeed("UB", "Urban Base", 2, 3));

    static final List<String> HOTEL_SUFFIXES = List.of(
            "Downtown", "Airport", "City Centre", "Riverside", "Old Town", "Convention Center", "Park", "Central Station");

    static final List<String> STREETS = List.of(
            "Main St", "Market St", "Harbour Rd", "Station Ave", "Park Blvd", "King St", "Liberty Ave", "Plaza Mayor");

    static final List<String> CAR_VENDORS = List.of("AX", "BR", "CR", "DV", "EV");

    static final List<CarSeed> CARS = List.of(
            new CarSeed("ECMR", "Economy (e.g. Kia Picanto)", 28),
            new CarSeed("CCAR", "Compact (e.g. VW Golf)", 34),
            new CarSeed("ICAR", "Intermediate (e.g. Toyota Corolla)", 41),
            new CarSeed("SCAR", "Standard (e.g. VW Jetta)", 47),
            new CarSeed("FCAR", "Fullsize (e.g. Toyota Camry)", 55),
            new CarSeed("IFAR", "Intermediate SUV (e.g. Toyota RAV4)", 62),
            new CarSeed("PCAR", "Premium (e.g. BMW 3 Series)", 84),
            new CarSeed("MVAR", "Minivan (e.g. Chrysler Pacifica)", 79),
            new CarSeed("LCAR", "Luxury (e.g. Mercedes E-Class)", 119));
}
