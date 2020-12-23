<html>
  <head>
    <title>Testing for Bad User Input</title>
  </head>
  <body>

    Use the below forms to expose a Cross Site Scripting (XSS) or
    HTML injection vulnerability, or to demonstrate SQL injection or
    command injection vulnerabilities.

    <br><br>

    <!-- Begin GET Method Search Form -->
    <table border="1">
      <tr>
        <td>
           Enter your search query (method="get"): 

          <form method="get">
            <input type="text" name="queryString1" width="20"
                   value="<%= request.getParameter("queryString1")%>"
            >
            <input type="hidden" name="hidden1" value="hiddenValue1">
            <input type="submit" name="submit1" value="Search">
          </form>
        </td>
        <td>
          queryString1 = <%= request.getParameter("queryString1") %><br>
          hidden1 =      <%= request.getParameter("hidden1") %><br>
          submit1 =      <%= request.getParameter("submit1") %><br>
        </td>
      </tr>
    </table>
    <!-- End GET Method Search Form -->

    <br>

    <!-- Begin POST Method Search Form -->
    <table border="1">
      <tr>
        <td>
           Enter your search query (method="post"): 

          <form method="post">
            <input type="text" name="queryString2" width="20"
                   value="<%= request.getParameter("queryString2")%>"
            >
            <input type="hidden" name="hidden2" value="hiddenValue2">
            <input type="submit" name="submit2" value="Search">
          </form>
        </td>
        <td>
          queryString2 = <%= request.getParameter("queryString2") %><br>
          hidden2 =      <%= request.getParameter("hidden2") %><br>
          submit2 =      <%= request.getParameter("submit2") %><br>
        </td>
      </tr>
    </table>
    <!-- End POST Method Search Form -->

    <br>

    <!-- Begin POST Method Username Form -->
    <table border="1">
      <tr>
        <td width="50%">
          <% // If we got a username, check it for validity.
             String username = request.getParameter("username");
             if (username != null) {
                 // Verify that the username contains only valid characters.
                 boolean validChars = true;
                 char[] usernameChars = username.toCharArray();
                 for (int i = 0; i < username.length(); i++) {
                     if (!Character.isLetterOrDigit(usernameChars[i])) {
                         validChars = false;
                         break;
                     }
                 }
                 if (!validChars) {
                     out.write("<font color=\"red\"><b><i>");
                     out.write("Username contained invalid characters. ");
                     out.write("Please use only A-Z, a-z, and 0-9.");
                     out.write("</i></b></font><br>");
                 }
                 // Verify that the username length is valid.
                 else if (username.length() < 3 || username.length() > 9) {
                     out.write("<font color=\"red\"><b><i>");
                     out.write("Bad username length. Must be 3-9 chars.");
                     out.write("</i></b></font><br>");
                 }
                 // Otherwise, it's valid.
                 else {
                     out.write("<center><i>\n");
                     out.write("Currently logged in as <b>" + username + "\n");
                     out.write("</b>.\n");
                     out.write("</i></center>\n");
                 }
             }
          %>

          Enter your username [3-9 alphanumeric characters]. (method="post"): 

          <form method="post">
            <input type="text" name="username" width="20"
                   value="<%= request.getParameter("username")%>"
            >
            <input type="hidden" name="hidden3" value="hiddenValue3">
            <input type="submit" name="submit3" value="Submit">
          </form>

        </td>
        <td>
          username = <%= request.getParameter("username") %><br>
          hidden3 =      <%= request.getParameter("hidden3") %><br>
          submit3 =      <%= request.getParameter("submit3") %><br>
        </td>
      </tr>
    </table>
    <!-- End POST Method Username Form -->

  </body>
</html>
