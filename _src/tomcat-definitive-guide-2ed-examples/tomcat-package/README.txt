Package Features
----------------

General features:
- Fully relocateable RPM package while still being software updateable.
- Two or more installations of the package may be installed and run at once,
  with just port and/or host address changes in each server.xml file.
- Package is renameable at build time without modifying any files.
  This feature, combined with the relocateable feature allow installation
  and operation without first removing any existing package named "tomcat".
- Each package installation has its own environment variable settings file
  that is used by the JVM process, which includes JVM startup parameters.


Build file features:
- Generates the source RPM (SRPM), the binary RPMs, and a tar.gz of each
  binary RPM's content.
- Supports per-developer build property settings overrides.
- Implements archiving release archives to an archival/distribution server.
- Implements a separate version properties file so that build file change
  history isn't obscured by numerous version number change commits.
- SRPM generates three binary RPMs: tomcat, tomcat-compat, and tomcat-admin.
- The spec file is written to make it as easy as possible for developers to
  upgrade the Tomcat 5.5 binary tar.gz files and pick up any new files that
  they contain, and package them in the correct package.
- Makes it easy to tag the packaging files in Subversion (and soon CVS).


Installing
----------

# rpm -ivh tomcat-6.0.xy-z.noarch.rpm

If you get the following error:

error: Failed dependencies:
         /bin/sh is needed by tomcat-6.0.xy-z.noarch

This usually occurs on operating systems that do not primarily use the
RPM package manager, and you are installing this Tomcat RPM package when
the RPM package manager's database is empty (no package in the database
provides the /bin/sh interpreter).  An example: installing the Tomcat RPM
package on a Debian Linux OS.

Try again to install it like this:

# rpm -ivh --nodeps tomcat-6.0.xy-z.noarch.rpm

If you get lots of warnings about users and groups, like this:

warning: user tomcat does not exist - using root
warning: group nobody does not exist - using root

Then you need to add a "tomcat" user and "nobody" group by hand using
adduser and addgroup.  Just make sure that the tomcat user's primary
group is "nobody".  Also: make sure that you set user tomcat's home
directory to "/opt/tomcat/temp", and set tomcat's login shell to
be something that doesn't actually work, like /sbin/nologin if you
have that.  

# groupadd nobody
# useradd -s /sbin/nologin -d /opt/tomcat/temp -c 'Tomcat User' -g nobody tomcat

Once you are done with this, try again to install the tomcat package:

# rpm -e tomcat
# rpm -ivh --nodeps tomcat-6.0.xy-z.noarch.rpm

Once the package has cleanly installed, edit /opt/tomcat/conf/tomcat-env.sh
and set JAVA_HOME to the correct path to your Java VM.


Running
-------

Start it like this:

# /etc/init.d/tomcat start

Or, this may also work:

# service tomcat start

Stop it like this:

# /etc/init.d/tomcat stop

Or:

# service tomcat stop

And, to restart Tomcat do this:

# service tomcat restart

Note that this package's init script implements reliable restarts -- when
it says that Tomcat has been restarted you can be sure that it has.  This
is untrue of just about all other Tomcat packages since they do not contain
code to robustly ensure that the Tomcat JVM does in fact restart.


Building
--------

Now, to use this, you must 1) unpack it.  2) copy the Tomcat release binary
tar.gz file into the root dir, and 3) type "ant".

The tar.gz file you need is:

apache-tomcat-6.0.xy.tar.gz,

Once you're able to build the package, edit the conf/tomcat-env.sh
file to match your machine's configuration best (JVM, other settings),
then do "ant clean build" in the root dir again to build the final
RPM package set.


Build Problems
--------------

# ant
Exception in thread "main" java.lang.NoClassDefFoundError: org/apache/tools/ant/launch/Launcher

This is caused by the installation of the jpackage.org ant RPM package.
Remove the package, and the Tomcat package set should build.
