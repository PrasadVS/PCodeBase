package com.nanobi.spark.dataframes;

import java.util.ArrayList; 
import java.util.HashMap; 
import java.util.List; 

import org.apache.avro.Schema; 
import org.apache.log4j.Logger; 
import org.apache.spark.SparkConf; 
import org.apache.spark.api.java.JavaPairRDD; 
import org.apache.spark.api.java.JavaRDD; 
import org.apache.spark.api.java.JavaSparkContext; 
import org.apache.spark.broadcast.Broadcast; 
//import org.apache.spark.sql.DataFrame; 
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row; 
import org.apache.spark.sql.RowFactory; 
import org.apache.spark.sql.SQLContext; 
import org.apache.spark.sql.types.DataType; 
import org.apache.spark.sql.types.DataTypes; 
import org.apache.spark.sql.types.StructField; 
import org.apache.spark.sql.types.StructType; 

import com.databricks.spark.avro.AvroSaver; 

import model.Arrest; 
import model.Play; 
import model.PlayData; 
import model.Stadium; 
import model.Weather; 
import scala.Tuple2; 
 
public class PlayByPlay { 
 
 public static Logger logger = Logger.getLogger(PlayByPlay.class); 
 
 public static void main(String[] args) { 
  SparkConf conf = new SparkConf(); 
  conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer"); 
 
  JavaSparkContext sc = new JavaSparkContext("local", "JavaAPISuite", conf); 
 
  final Broadcast<HashMap<String, ArrayList<String>>> teamSeasonToPlayersArrested = sc 
    .broadcast(loadArrests("../arrests.csv", sc)); 
 
  JavaPairRDD<String, String> lines = sc.wholeTextFiles("../input/2013_nfl_pbp_data_through_wk_4.csv"); 
  JavaRDD<PlayData> plays = lines.flatMap((Tuple2<String, String> pair) -> { 
   PlayByPlayParser playByPlayParser = new PlayByPlayParser(); 
   return playByPlayParser.parsePlayFile(pair._2(), pair._1()); 
  }).map((Play p) -> ArrestParser.parseArrest(p, teamSeasonToPlayersArrested.getValue())); 
 
  SQLContext sqlContext = new SQLContext(sc); 
 
  // Create the playbyplay table 
  createPlayByPlay(plays, sqlContext); 
 
  // Create the stadium table 
  createStadiums(sc, sqlContext); 
 
  // Create the weather table 
  createWeather(sc, sqlContext); 
 
  // Join all four datasets 
  Dataset<Row> join = sqlContext.sql("select *, " 
    + "(wv07 > 0 OR wv01 > 0 OR wv20 > 0 OR wv03 > 0) as hasWeatherInVicinity, " 
    + "(wt09 > 0 OR wt14 > 0 OR wt07 > 0 OR wt01 > 0 OR wt15 > 0 OR wt17 > 0 OR " 
    + "wt06 > 0 OR wt21 > 0 OR wt05 > 0 OR  wt02 > 0 OR wt11 > 0 OR wt22 > 0 OR " 
    + "wt04 > 0 OR wt13 > 0 OR wt16 > 0 OR  wt08 > 0 OR wt18 > 0 OR wt03 > 0 OR " 
    + "wt10 > 0 OR wt19 > 0) as hasWeatherType, " + "(wv07 > 0 OR wv01 > 0 OR wv20 > 0 OR wv03 > 0 OR " 
    + "wt09 > 0 OR wt14 > 0 OR wt07 > 0 OR wt01 > 0 OR wt15 > 0 OR wt17 > 0 OR " 
    + "wt06 > 0 OR wt21 > 0 OR wt05 > 0 OR  wt02 > 0 OR wt11 > 0 OR wt22 > 0 OR " 
    + "wt04 > 0 OR wt13 > 0 OR wt16 > 0 OR  wt08 > 0 OR wt18 > 0 OR wt03 > 0 OR " 
    + "wt10 > 0 OR wt19 > 0) as hasWeather " + " from playbyplay " 
    + "join stadium on stadium.team = playbyplay.hometeam " 
    + "left outer join weather on stadium.weatherstation = weather.station and playbyplay.dateplayed = weather.readingdate"); 
 
  // Save out as Avro 
  AvroSaver.save(join, "output"); 
 } 
 
 private static void createWeather(JavaSparkContext sc, SQLContext sqlContext) { 
  JavaRDD<Weather> weather = sc.textFile("../173328.csv").map((String line) -> { 
   return WeatherParser.parseWeather(line); 
  }); 
 
  StructType schema = getStructType(new Schema[] { Weather.SCHEMA$ }); 
 
  JavaRDD<Row> rowRDD = weather.map((Weather weatherRow) -> { 
   return RowFactory.create(weatherRow.get(0), weatherRow.get(1), weatherRow.get(2), weatherRow.get(3), 
     weatherRow.get(4), weatherRow.get(5), weatherRow.get(6), weatherRow.get(7), weatherRow.get(8), 
     weatherRow.get(9), weatherRow.get(10), weatherRow.get(11), weatherRow.get(12), weatherRow.get(13), 
     weatherRow.get(14), weatherRow.get(15), weatherRow.get(16), weatherRow.get(17), weatherRow.get(18), 
     weatherRow.get(19), weatherRow.get(20), weatherRow.get(21), weatherRow.get(22), weatherRow.get(23), 
     weatherRow.get(24), weatherRow.get(25), weatherRow.get(26), weatherRow.get(27), weatherRow.get(28), 
     weatherRow.get(29), weatherRow.get(30), weatherRow.get(31), weatherRow.get(32), weatherRow.get(33), 
     weatherRow.get(34), weatherRow.get(34), weatherRow.get(36), weatherRow.get(37), weatherRow.get(38), 
     weatherRow.get(39), weatherRow.get(40), weatherRow.get(41), weatherRow.get(42), weatherRow.get(43), 
     weatherRow.get(44), weatherRow.get(45), weatherRow.get(46), weatherRow.get(47), weatherRow.get(48)); 
  }); 
 
  // Apply the schema to the RDD. 
  Dataset<Row> weatherFrame = sqlContext.createDataFrame(rowRDD, schema); 
  weatherFrame.registerTempTable("weather"); 
 } 
 
 private static void createStadiums(JavaSparkContext sc, SQLContext sqlContext) { 
  JavaRDD<Stadium> stadiums = sc.textFile("../stadiums.csv").map((String line) -> { 
   return StadiumParser.parseStadium(line); 
  }); 
 
  StructType schema = getStructType(new Schema[] { Stadium.SCHEMA$ }); 
 
  JavaRDD<Row> rowRDD = stadiums.map((Stadium stadiumRow) -> { 
   return RowFactory.create(stadiumRow.get(0), stadiumRow.get(1), stadiumRow.get(2), stadiumRow.get(3), 
     stadiumRow.get(4), stadiumRow.get(5), stadiumRow.get(6), stadiumRow.get(7), stadiumRow.get(8), 
     stadiumRow.get(9), stadiumRow.get(10)); 
  }); 
 
  // Apply the schema to the RDD. 
  Dataset<Row> stadiumFrame = sqlContext.createDataFrame(rowRDD, schema); 
  stadiumFrame.registerTempTable("stadium"); 
 } 
 
 private static void createPlayByPlay(JavaRDD<PlayData> plays, SQLContext sqlContext) { 
  StructType schema = getStructType(new Schema[] { Play.SCHEMA$, Arrest.SCHEMA$ }); 
 
  // Only plays and arrest exist so far 
  JavaRDD<Row> rowRDD = plays.map((PlayData playData) -> { 
   return RowFactory.create(playData.getPlay().get(0), playData.getPlay().get(1), playData.getPlay().get(2), 
     playData.getPlay().get(3), playData.getPlay().get(4), playData.getPlay().get(5), 
     playData.getPlay().get(6), playData.getPlay().get(7), playData.getPlay().get(8), 
     playData.getPlay().get(9), playData.getPlay().get(10), playData.getPlay().get(11), 
     playData.getPlay().get(12), playData.getPlay().get(13), playData.getPlay().get(14), 
     playData.getPlay().get(15), playData.getPlay().get(16), playData.getPlay().get(17), 
     playData.getPlay().get(18), playData.getPlay().get(19), playData.getPlay().get(20), 
     playData.getPlay().get(21), playData.getPlay().get(22), playData.getPlay().get(23), 
     playData.getPlay().get(24), playData.getPlay().get(25), playData.getPlay().get(26), 
     playData.getPlay().get(27), playData.getPlay().get(28), playData.getArrest().get(0), 
     playData.getArrest().get(1), playData.getArrest().get(2), playData.getArrest().get(3), 
     playData.getArrest().get(4)); 
  }); 
 
  // Apply the schema to the RDD. 
  DataFrame playsAndArrestsFrame = sqlContext.createDataFrame(rowRDD, schema); 
  playsAndArrestsFrame.registerTempTable("playbyplay"); 
 } 
 
 private static HashMap<String, ArrayList<String>> loadArrests(String arrestsFile, JavaSparkContext sc) { 
  JavaRDD<String> input = sc.textFile(arrestsFile, 1); 
 
  HashMap<String, ArrayList<String>> teamSeasonToPlayersArrested = new HashMap<String, ArrayList<String>>(); 
 
  input.collect().forEach((String line) -> { 
   String[] pieces = line.split(","); 
 
   String key = ArrestParser.getKey(pieces[0], pieces[1]); 
 
   ArrayList<String> arrestsPerSeasonAndTeam = teamSeasonToPlayersArrested.get(key); 
 
   if (arrestsPerSeasonAndTeam == null) { 
    arrestsPerSeasonAndTeam = new ArrayList<String>(); 
    teamSeasonToPlayersArrested.put(key, arrestsPerSeasonAndTeam); 
   } 
 
   arrestsPerSeasonAndTeam.add(pieces[2]); 
  }); 
 
  return teamSeasonToPlayersArrested; 
 } 
 
 private static StructType getStructType(Schema[] schemas) { 
  // Workaround because Spark SQL doesn't support Avro directly 
  List<StructField> fields = new ArrayList<StructField>(); 
 
  for (Schema schema : schemas) { 
   for (Schema.Field field : schema.getFields()) { 
    field.schema().getType(); 
 
    // Spark SQL seems to be case sensitive 
    // Normalizing to all lower case 
    fields.add(DataTypes.createStructField(field.name().toLowerCase(), getDataTypeForAvro(field.schema()), 
      true)); 
   } 
  } 
 
  return DataTypes.createStructType(fields); 
 } 
 
 private static DataType getDataTypeForAvro(Schema schema) { 
  DataType returnDataType = DataTypes.StringType; 
 
  switch (schema.getType()) { 
  case INT: 
   returnDataType = DataTypes.IntegerType; 
   break; 
  case STRING: 
   returnDataType = DataTypes.StringType; 
   break; 
  case BOOLEAN: 
   returnDataType = DataTypes.BooleanType; 
   break; 
  case BYTES: 
   returnDataType = DataTypes.ByteType; 
   break; 
  case DOUBLE: 
   returnDataType = DataTypes.DoubleType; 
   break; 
  case FLOAT: 
   returnDataType = DataTypes.FloatType; 
   break; 
  case LONG: 
   returnDataType = DataTypes.LongType; 
   break; 
  case FIXED: 
   returnDataType = DataTypes.BinaryType; 
   break; 
  case ENUM: 
   returnDataType = DataTypes.StringType; 
   break; 
  } 
 
  return returnDataType; 
 } 
}


 