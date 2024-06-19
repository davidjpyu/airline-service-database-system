package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import flightapp.PasswordUtils;
/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;
  private PreparedStatement createStmt;
  private PreparedStatement loginStmt;
  private PreparedStatement searchDirectCountStmt;
  private PreparedStatement searchDirectStmt;
  private PreparedStatement searchDirectAndIndirectStmt;
  private PreparedStatement flightInfoStmt1;
  private PreparedStatement flightInfoStmt2;
  private PreparedStatement checkDate;
  private PreparedStatement checkReservedAmount;
  private PreparedStatement bookDirectStmt;
  private PreparedStatement bookIndirectStmt;
  private PreparedStatement checkReservationStmt;
  private PreparedStatement checkBalanceStmt;
  private PreparedStatement checkAllReservationStmt;
  private PreparedStatement getReservationId;
  //
  // Instance variables
  //
  private Boolean loggedIn = false;
  private ArrayList<int[]> searchResult = new ArrayList<>();
  private String username = "";


  protected Query() throws SQLException, IOException {
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      Statement clearTable = conn.createStatement();
      clearTable.execute("DELETE FROM RESERVATIONS_jpyu");
      clearTable.execute("DELETE FROM USERS_jpyu");
      clearTable.execute("DELETE FROM SEATRESERVED_jpyu");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);

    // TODO: YOUR CODE HERE
    createStmt = conn.prepareStatement("INSERT INTO USERS_jpyu VALUES (?, ?, ?)");
    loginStmt = conn.prepareStatement("SELECT * FROM USERS_jpyu WHERE username = ?");
    searchDirectCountStmt = conn.prepareStatement("SELECT COUNT(*) FROM FLIGHTS \n" +
                                                  "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled = 0");
    searchDirectStmt = conn.prepareStatement("SELECT TOP(?) F.actual_time AS actual_time, F.fid AS fid1, NULL AS fid2, 1 AS isDirect \n" +
                                              "FROM FLIGHTS AS F \n" +
                                              "WHERE F.origin_city = ? AND F.dest_city = ? AND F.day_of_month = ? AND F.canceled = 0 \n" + 
                                              "ORDER BY F.actual_time ASC, F.fid ASC");
    searchDirectAndIndirectStmt = conn.prepareStatement(
                                              "SELECT * FROM \n" +
                                              "(SELECT TOP(?) F.actual_time AS actual_time, F.fid AS fid1, NULL AS fid2, 1 AS isDirect \n" +
                                              "FROM FLIGHTS AS F \n" +
                                              "WHERE F.origin_city = ? AND F.dest_city = ? AND F.day_of_month = ? AND F.canceled = 0 \n" + 
                                              "ORDER BY F.actual_time ASC, F.fid ASC) AS a \n" +
                                              "UNION \n" +
                                              "SELECT * FROM \n" +
                                              "(SELECT TOP(?) F1.actual_time + F2.actual_time AS actual_time, F1.fid AS fid1, F2.fid AS fid2, 0 AS isDirect \n" +
                                              "FROM FLIGHTS AS F1, FLIGHTS AS F2 \n" +
                                              "WHERE F1.origin_city = ? AND F1.dest_city = F2.origin_city AND \n" +
                                              "      F2.dest_city = ? AND F1.canceled = 0 AND F2.canceled = 0 AND \n" +
                                              "      F1.day_of_month = ? AND F2.day_of_month = F1.day_of_month\n" +
                                              "ORDER BY F1.actual_time+F2.actual_time ASC, F1.fid ASC, F2.fid ASC) AS b \n" +
                                              "ORDER BY actual_time ASC, fid1 ASC, fid2 ASC"
                                              );
    flightInfoStmt1 = conn.prepareStatement("SELECT fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price \n" +
                                            "FROM FLIGHTS \n" +
                                            "WHERE fid = ?");
    flightInfoStmt2 = conn.prepareStatement("SELECT fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price \n" +
                                            "FROM FLIGHTS \n" +
                                            "WHERE fid = ?");
    checkDate = conn.prepareStatement("SELECT * \n" +
                                      "FROM FLIGHTS AS F, RESERVATIONS_jpyu AS R WITH (UPDLOCK) \n" +
                                      "WHERE F.day_of_month = ? AND \n" +
                                      "(F.fid = R.fid1 OR F.fid = R.fid2) AND \n" +
                                      "R.username = ?");
    checkReservedAmount = conn.prepareStatement("SELECT * \n " +
                                          "FROM SEATRESERVED_jpyu AS SR WITH(UPDLOCK)\n" +
                                          "WHERE fid = ?");
    bookDirectStmt = conn.prepareStatement("INSERT INTO RESERVATIONS_jpyu \n" +
                                            "SELECT COUNT(*)+1, 0, ?, NULL, ? \n" +
                                            "FROM RESERVATIONS_jpyu");
    bookIndirectStmt = conn.prepareStatement("INSERT INTO RESERVATIONS_jpyu \n" +
                                            "SELECT COUNT(*)+1, 0, ?, ?, ? \n" +
                                            "FROM RESERVATIONS_jpyu");
    checkReservationStmt = conn.prepareStatement("SELECT * FROM RESERVATIONS_jpyu WITH(UPDLOCK) WHERE username = ? AND rid = ? AND paid = 0");
    checkBalanceStmt = conn.prepareStatement("SELECT balance FROM USERS_jpyu WHERE username = ?");
    checkAllReservationStmt = conn.prepareStatement("SELECT * FROM RESERVATIONS_jpyu WHERE username = ? ORDER BY rid ASC");
    getReservationId = conn.prepareStatement("SELECT rid FROM RESERVATIONS_jpyu WHERE username = ? AND fid1 = ?");
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n".  For all
   *         other errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    // TODO: YOUR CODE HERE
    try {
      if (loggedIn) {
        return "User already logged in\n";
      }

      loginStmt.setString(1, username);
      ResultSet result = loginStmt.executeQuery();
      if (result.next()) {
        if (PasswordUtils.plaintextMatchesHash(password, result.getBytes("password"))) {
          loggedIn = true;
          result.close();
          this.username = username;
          return "Logged in as " + username + "\n";
        }
      }
      return "Login failed\n";
    } catch (Exception e) {
      return "Login failed\n";
    }
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    // TODO: YOUR CODE HERE
    try{
      if (initAmount < 0) {
        return "Failed to create user\n";
      }
      createStmt.setString(1, username);
      createStmt.setBytes(2, PasswordUtils.hashPassword(password));
      createStmt.setInt(3, initAmount);
      createStmt.execute();

      return "Created user " + username + "\n";
    } catch (Exception e) {
      return "Failed to create user\n";
    }
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given
   * day of the month. If {@code directFlight} is true, it only searches for direct flights,
   * otherwise is searches for direct flights and flights with two "hops." Only searches for up
   * to the number of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return, must be positive
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // TODO: YOUR CODE HERE

    StringBuffer sb = new StringBuffer();

    try {
      searchDirectCountStmt.setString(1, originCity);
      searchDirectCountStmt.setString(2, destinationCity);
      searchDirectCountStmt.setInt(3, dayOfMonth);
      ResultSet directNum = searchDirectCountStmt.executeQuery();
      directNum.next();
      int directCount = directNum.getInt(1);
      directNum.close();

      ResultSet result = null;
      if(!directFlight && directCount < numberOfItineraries) {
        int maxAllowLeft = numberOfItineraries - directCount;
        if (maxAllowLeft > 0) {
          searchDirectAndIndirectStmt.setInt(1, directCount);
          searchDirectAndIndirectStmt.setString(2, originCity);
          searchDirectAndIndirectStmt.setString(3, destinationCity);
          searchDirectAndIndirectStmt.setInt(4, dayOfMonth);
          searchDirectAndIndirectStmt.setInt(5, maxAllowLeft);
          searchDirectAndIndirectStmt.setString(6, originCity);
          searchDirectAndIndirectStmt.setString(7, destinationCity);
          searchDirectAndIndirectStmt.setInt(8, dayOfMonth);
          result = searchDirectAndIndirectStmt.executeQuery();
        }
      } else {
        searchDirectStmt.setInt(1, numberOfItineraries);
        searchDirectStmt.setString(2, originCity);
        searchDirectStmt.setString(3, destinationCity);
        searchDirectStmt.setInt(4, dayOfMonth);
        result = searchDirectStmt.executeQuery();
      }

      searchResult = new ArrayList<>();
      while (result.next()) {
        if(result.getInt("isDirect") == 0){
          searchResult.add(new int[] {result.getInt("fid1"), result.getInt("fid2")});
          flightInfoStmt1.setInt(1, result.getInt("fid1"));
          ResultSet flightInfo1 = flightInfoStmt1.executeQuery();
          flightInfo1.next();
          flightInfoStmt2.setInt(1, result.getInt("fid2"));
          ResultSet flightInfo2 = flightInfoStmt2.executeQuery();
          flightInfo2.next();
          sb.append("Itinerary " + (searchResult.size()-1) + ": 2 flight(s), " + (flightInfo1.getInt("actual_time")+flightInfo2.getInt("actual_time")) + " minutes\n");
          Flight flight1 = new Flight(flightInfo1.getInt("fid"), flightInfo1.getInt("day_of_month"), flightInfo1.getString("carrier_id"), flightInfo1.getString("flight_num"), flightInfo1.getString("origin_city"), flightInfo1.getString("dest_city"), flightInfo1.getInt("actual_time"), flightInfo1.getInt("capacity"), flightInfo1.getInt("price"));
          Flight flight2 = new Flight(flightInfo2.getInt("fid"), flightInfo2.getInt("day_of_month"), flightInfo2.getString("carrier_id"), flightInfo2.getString("flight_num"), flightInfo2.getString("origin_city"), flightInfo2.getString("dest_city"), flightInfo2.getInt("actual_time"), flightInfo2.getInt("capacity"), flightInfo2.getInt("price"));
          sb.append(flight1.toString() + "\n");
          sb.append(flight2.toString() + "\n");
          flightInfo1.close();
          flightInfo2.close();
        } else {
          searchResult.add(new int[] {result.getInt("fid1")});
          flightInfoStmt1.setInt(1, result.getInt("fid1"));
          ResultSet flightInfo1 = flightInfoStmt1.executeQuery();
          flightInfo1.next();
          sb.append("Itinerary " + (searchResult.size()-1) + ": 1 flight(s), " + flightInfo1.getInt("actual_time") + " minutes\n");
          Flight flight1 = new Flight(flightInfo1.getInt("fid"), flightInfo1.getInt("day_of_month"), flightInfo1.getString("carrier_id"), flightInfo1.getString("flight_num"), flightInfo1.getString("origin_city"), flightInfo1.getString("dest_city"), flightInfo1.getInt("actual_time"), flightInfo1.getInt("capacity"), flightInfo1.getInt("price"));
          sb.append(flight1.toString() + "\n");
          flightInfo1.close();
        }
      }
      result.close();
      if (!loggedIn) {
        searchResult = new ArrayList<>();
      }
    } catch (SQLException e) {
      System.out.println("Failed to search\n");
      e.printStackTrace();
    }
    return sb.toString();
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search
   *                    in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged
   *         in\n". If the user is trying to book an itinerary with an invalid ID or without
   *         having done a search, then return "No such itinerary {@code itineraryId}\n". If the
   *         user already has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same day\n". For all
   *         other errors, return "Booking failed\n".
   *
   *         If booking succeeds, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from
   *         1 and increments by 1 each time a successful reservation is made by any user in
   *         the system.
   */
  public String transaction_book(int itineraryId) {
    // TODO: YOUR CODE HERE
    if (!loggedIn) {
      return "Cannot book reservations, not logged in\n";
    }
    if (itineraryId >= searchResult.size()) {
      return "No such itinerary " + itineraryId + "\n";
    }

    int[] itinerary = searchResult.get(itineraryId);
    try{
      Statement pickDate = conn.createStatement();
      ResultSet date = pickDate.executeQuery("SELECT day_of_month FROM FLIGHTS WHERE fid = " + itinerary[0]);
      date.next();
      int day = date.getInt("day_of_month");
      conn.setAutoCommit(false);
      try {
        checkDate.setInt(1, day);
        checkDate.setString(2, username);
        ResultSet check = checkDate.executeQuery();
        if (check.next()) {
          conn.commit();
          conn.setAutoCommit(true);
          return "You cannot book two flights in the same day\n";
        }
        checkReservedAmount.setInt(1, itinerary[0]);
        ResultSet reserved1 = checkReservedAmount.executeQuery();
        int reserved1Num = 0;
        int reserved2Num = 0;
        if (reserved1.next()) {
          reserved1Num = reserved1.getInt("reserved");
        }
        reserved1.close();
        flightCapacityStmt.setInt(1, itinerary[0]);
        ResultSet capacity1 = flightCapacityStmt.executeQuery();
        capacity1.next();
        int capacity1Num = capacity1.getInt("capacity");
        if (reserved1Num >= capacity1Num) {
          conn.commit();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
        if (itinerary.length == 2) {
          checkReservedAmount.setInt(1, itinerary[1]);
          ResultSet reserved2 = checkReservedAmount.executeQuery();
          if (reserved2.next()) {
            reserved2Num = reserved2.getInt("reserved");
          }
          reserved2.close();
          flightCapacityStmt.setInt(1, itinerary[1]);
          ResultSet capacity2 = flightCapacityStmt.executeQuery();
          capacity2.next();
          int capacity2Num = capacity2.getInt("capacity");
          if (reserved2Num >= capacity2Num) {
            conn.commit();
            conn.setAutoCommit(true);
            return "Booking failed\n";
          }
        }
        if (reserved1Num == 0) {
          Statement addSeatReserved = conn.createStatement();
          addSeatReserved.executeUpdate("INSERT SEATRESERVED_jpyu VALUES (" + itinerary[0] + ", 1)");
        } else {
          Statement addSeatReserved = conn.createStatement();
          addSeatReserved.executeUpdate("UPDATE SEATRESERVED_jpyu SET reserved = reserved + 1 WHERE fid = " + itinerary[0]);
        }
        if (itinerary.length == 2) {
          if (reserved2Num == 0) {
            Statement addSeatReserved = conn.createStatement();
            addSeatReserved.executeUpdate("INSERT SEATRESERVED_jpyu VALUES (" + itinerary[1] + ", 1)");
          } else {
            Statement addSeatReserved = conn.createStatement();
            addSeatReserved.executeUpdate("UPDATE SEATRESERVED_jpyu SET reserved = reserved + 1 WHERE fid = " + itinerary[1]);
          }
        }

        if (itinerary.length == 1) {
          bookDirectStmt.setInt(1, itinerary[0]);
          bookDirectStmt.setString(2, username);
          bookDirectStmt.executeUpdate();
        } else {
          bookIndirectStmt.setInt(1, itinerary[0]);
          bookIndirectStmt.setInt(2, itinerary[1]);
          bookIndirectStmt.setString(3, username);
          bookIndirectStmt.executeUpdate();
        }
        conn.commit();
        conn.setAutoCommit(true);
      } catch (SQLException e) {
        conn.rollback();
        conn.setAutoCommit(true);
        e.printStackTrace();
        return "Booking failed\n";
      }
        getReservationId.setString(1, username);
        getReservationId.setInt(2, itinerary[0]);
        ResultSet reservationId = getReservationId.executeQuery();
        reservationId.next();
        int reservationIdNum = reservationId.getInt("rid");
        return "Booked flight(s), reservation ID: " + reservationIdNum + "\n";
    } catch (SQLException e) {
      e.printStackTrace();
      return "Booking failed\n";
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n". If the
   *         reservation is not found / not under the logged in user's name, then return
   *         "Cannot find unpaid reservation [reservationId] under user: [username]\n".  If
   *         the user does not have enough money in their account, then return
   *         "User has only [balance] in account but itinerary costs [cost]\n".  For all other
   *         errors, return "Failed to pay for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    // TODO: YOUR CODE HERE
    if (!loggedIn) {
      return "Cannot pay, not logged in\n";
    }
    try{
      conn.setAutoCommit(false);
      try {
        checkReservationStmt.setString(1, username);
        checkReservationStmt.setInt(2, reservationId);
        ResultSet check = checkReservationStmt.executeQuery();
        if (!check.next()) {
          conn.commit();
          conn.setAutoCommit(true);
          return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
        }
        int fid1 = check.getInt("fid1");
        int fid2 = check.getInt("fid2");
        int cost = 0;
        if (fid2 == 0) {
          Statement totalCost = conn.createStatement();
          ResultSet result = totalCost.executeQuery("SELECT price FROM FLIGHTS WHERE fid = " + fid1);
          result.next();
          cost = result.getInt("price");
        } else {
          Statement totalCost = conn.createStatement();
          ResultSet result = totalCost.executeQuery("SELECT price FROM FLIGHTS WHERE fid = " + fid1);
          result.next();
          cost = result.getInt("price");
          result = totalCost.executeQuery("SELECT price FROM FLIGHTS WHERE fid = " + fid2);
          result.next();
          cost += result.getInt("price");
        }
        checkBalanceStmt.setString(1, username);
        ResultSet balance = checkBalanceStmt.executeQuery();
        balance.next();
        int balanceNum = balance.getInt("balance");
        if (balanceNum < cost) {
          conn.commit();
          conn.setAutoCommit(true);
          return "User has only " + balanceNum + " in account but itinerary costs " + cost + "\n";
        }
        Statement pay = conn.createStatement();
        pay.executeUpdate("UPDATE RESERVATIONS_jpyu SET paid = 1 WHERE rid = " + reservationId);
        pay.executeUpdate("UPDATE USERS_jpyu SET balance = " + (balanceNum - cost) + " WHERE username = '" + username + "'");
        conn.commit();
        conn.setAutoCommit(true);
        return "Paid reservation: " + reservationId + " remaining balance: " + (balanceNum - cost) + "\n";
      } catch (SQLException e) {
        conn.rollback();
        conn.setAutoCommit(true);
        e.printStackTrace();
        return "Failed to pay for reservation " + reservationId + "\n";
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to pay for reservation " + reservationId + "\n";
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    // TODO: YOUR CODE HERE
    StringBuffer sb = new StringBuffer();
    try{
      if (!loggedIn) {
        return "Cannot view reservations, not logged in\n";
      }
      checkAllReservationStmt.setString(1, username);
      ResultSet result = checkAllReservationStmt.executeQuery();
      if (!result.next()) {
        return "No reservations found\n";
      }
      do {
        int rid = result.getInt("rid");
        int fid1 = result.getInt("fid1");
        int fid2 = result.getInt("fid2");
        boolean paid = result.getBoolean("paid");
        sb.append("Reservation " + rid + " paid: " + paid + ":\n");
        Statement getFlight = conn.createStatement();
        ResultSet flightInfo1 = getFlight.executeQuery("SELECT * FROM FLIGHTS WHERE fid = " + fid1);
        flightInfo1.next();
        Flight flight1 = new Flight(flightInfo1.getInt("fid"), flightInfo1.getInt("day_of_month"), flightInfo1.getString("carrier_id"), flightInfo1.getString("flight_num"), flightInfo1.getString("origin_city"), flightInfo1.getString("dest_city"), flightInfo1.getInt("actual_time"), flightInfo1.getInt("capacity"), flightInfo1.getInt("price"));
        sb.append(flight1.toString() + "\n");
        if (fid2 != 0) {
          ResultSet flightInfo2 = getFlight.executeQuery("SELECT * FROM FLIGHTS WHERE fid = " + fid2);
          flightInfo2.next();
          Flight flight2 = new Flight(flightInfo2.getInt("fid"), flightInfo2.getInt("day_of_month"), flightInfo2.getString("carrier_id"), flightInfo2.getString("flight_num"), flightInfo2.getString("origin_city"), flightInfo2.getString("dest_city"), flightInfo2.getInt("actual_time"), flightInfo2.getInt("capacity"), flightInfo2.getInt("price"));
          sb.append(flight2.toString() + "\n");
        }
      } while (result.next());
      return sb.toString();
    } catch (SQLException e) {
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
