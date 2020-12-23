# This spec file is configured by the Ant build.xml file that comes
# with it.  If you want to change variable values, change them either
# by invoking the Ant build with properties on the command line
# (like "ant -Dpackage.name=foo") or by setting property values in a
# properties file or in build.xml itself.  If you only have the SRPM,
# however, then this is the place to change the values.

# The name of this RPM.  This can be changed slightly at build time to
# allow generating and installing more than one package for this
# component at the same time on the same computer, just with different
# installation prefixes and JVM_IDs.
%define package_name @PACKAGE_NAME@

# The JVM ID name that this Tomcat JVM should identify itself as.  You
# can name the JVM ID anything as long as it doesn't contain spaces or
# quotes, or other special shell characters like |, >, !, $, or &.
%define jvm_id @JVM_ID@

# The username of the user account that the Tomcat instance will run as.
%define tomcat_user @TOMCAT_USER@

# The user ID of the user account named in %{tomcat_username}.
%define tomcat_uid @TOMCAT_UID@

# The name of the group to place the Tomcat user in.
%define tomcat_group @TOMCAT_GROUP@

# The group ID of the Tomcat group.
%define tomcat_gid @TOMCAT_GID@

# The default absolute file system prefix under which the files are installed.
%define default_install_prefix /opt/%{package_name}

# We need the following line so that RPM can find the BUILD and SOURCES dirs.
%define _topdir @TOP_DIR@

Summary: The Tomcat Servlet and JSP container.
Name: %{package_name}
Version: @VERSION@
Release: @BUILD_SERIAL@
License: Apache License v2.0
Vendor: Jason Brittain
Group: Networking/Daemons
URL: http://www.webdroid.org:8080/repo/viewcvs.cgi/tomcat-package/trunk/
Source0: apache-tomcat-%{version}.tar.gz
Source3: %{package_name}-init.linux
Source4: %{package_name}-env.sh
Source5: server.xml
Source6: web.xml
Source7: tomcat-users.xml
Source8: logging.properties
Source9: ROOT.xml
BuildRoot: %{_topdir}/BUILD/%{package_name}
BuildArch: noarch
Prefix: %{default_install_prefix}
#Requires: # Change this line so this package requires your choice of JVM RPM.
Provides: %{package_name}

%description
The Tomcat Servlet and JSP container implements Sun Microsystems'
Java Servlet 2.5 and Java Server Pages (JSP) 2.1 Specifications.

This additional packaging and runtime script code was initially
written as part of Tomcat: The Definitive Guide, 2nd Edition by
Jason Brittain and Ian Darwin.  It is released under the same license
as Tomcat -- the Apache License, Version 2.0.

$Id: tomcat.spec 96 2007-07-01 07:50:52Z jasonb $


%prep
cd %{_topdir}/BUILD
rm -rf %{package_name}

# Unpack the already-built server binaries.
tar -zxf %{_topdir}/SOURCES/apache-tomcat-%{version}.tar.gz

# Make the paths resemble the package's default deployment paths.
mv apache-tomcat-%{version} %{package_name}

# Copy the stock server.xml to server.xml.stock.
cp %{package_name}/conf/server.xml %{package_name}/conf/server.xml.stock || :

# Copy the custom config files into conf/.
cp %{_topdir}/SOURCES/server.xml %{_topdir}/SOURCES/web.xml \
    %{_topdir}/SOURCES/catalina.properties \
    %{_topdir}/SOURCES/tomcat-users.xml \
    %{_topdir}/SOURCES/logging.properties %{package_name}/conf/ || :

# Copy ROOT.xml if it's in SOURCES, otherwise it's empty and ends up deleted.
touch %{_topdir}/SOURCES/ROOT.xml
mkdir -p %{package_name}/conf/Catalina/localhost || :
cp %{_topdir}/SOURCES/ROOT.xml %{package_name}/conf/Catalina/localhost/ || :
if [ ! -s %{package_name}/conf/Catalina/localhost/ROOT.xml ]; then
    rm %{package_name}/conf/Catalina/localhost/ROOT.xml || :
fi

# Move the init and conf scripts into the proper dirs.
cp %{_topdir}/SOURCES/%{package_name}-init.linux \
    %{package_name}/bin/%{package_name}
cp %{_topdir}/SOURCES/%{package_name}-env.sh %{package_name}/conf/

# Additionally encapsulate the server files in the /opt/%{package_name} path.
mkdir -p opt/
mv %{package_name} opt/
mkdir %{package_name}
mv opt %{package_name}/

# Make file lists for the main Tomcat files.
find %{package_name} | cut -d'/' -f 2- | grep -xv %{package_name} \
    > .server-ant-files.txt || :
cat .server-ant-files.txt | xargs -i echo "/{}" >.server-files.txt || :

# Build the final file lists.  Each list excludes the other package's files.
cat .server-files.txt > .server.txt

# Apply any patches here.  (none currently)
#pushd %{package_name}/opt/%{package_name}
#%patch0 -p0
#popd

# Set the owner and group to root if we're building as root.
[ `id -u` = '0' ] && chown -Rhf root %{package_name}
[ `id -u` = '0' ] && chgrp -Rhf root %{package_name}
chmod -Rf a+rX,g-w,o-w %{package_name}

# Set some permissions specially.
chmod 755 %{package_name}%{default_install_prefix}/bin
chmod 750 %{package_name}%{default_install_prefix}/bin/*.sh
chmod 750 %{package_name}%{default_install_prefix}/bin/%{package_name}
chmod 750 %{package_name}%{default_install_prefix}/conf
chmod 750 %{package_name}%{default_install_prefix}/conf/Catalina
chmod 775 %{package_name}%{default_install_prefix}/temp
chmod 770 %{package_name}%{default_install_prefix}/webapps
chmod 775 %{package_name}%{default_install_prefix}/work

%build

%install

%clean

%pre
# Set some variables we need.
if [ $1 = 2 ]; then
    # We're upgrading (rpm -U) or "reinstalling" (rpm -U --force) the package.

    # Find out what the prefix of the already-installed package is before
    # the upgrade.
    BEFORE_PREFIX="`rpm -q --queryformat '%{INSTALLPREFIX}' %{package_name}`"

    # If we're relocating the package to a new prefix versus the package
    # we're upgrading from, remove just the verifiable files and empty dirs
    # from the already-installed package so we can write them into the new
    # prefix.
    if [ "$BEFORE_PREFIX" != "$RPM_INSTALL_PREFIX" ]; then
        # Get a list of the files that are not verifiable.
        NON_VERIFIABLES="`rpm -V %{package_name} | cut -c 13-`"

        # Loop through each already-installed file path of this package.
        for pathname in `rpm -ql %{package_name}`; do
            if [ -d "$pathname" ] ; then
                # It's a directory, so delete it and its parents if empty.
                rmdir -p "$pathname" >/dev/null 2>&1
            else
                # It's not a directory, so try to delete it.
                echo -e $NON_VERIFIABLES | grep -x "$pathname" >/dev/null 2>&1
                if [ $? == 1 ]; then
                    # It's verifiable, so we can safely delete it.
                    rm "$pathname" >/dev/null 2>&1

                    # If removing the file left the file's dir empty, try
                    # to remove the directory and its empty parents as well.
                    rmdir -p "`dirname $pathname`" >/dev/null 2>&1
                fi
            fi
        done
    fi
fi

# Add the Tomcat user account if it doesn't already exist.
TOMCAT_SHELL="/sbin/nologin"
if [ ! -x /etc/rc.d/init.d/functions -o ! -x /sbin/runuser ]; then
    # We will need to use su to run Tomcat as the TOMCAT_USER, so
    # this user must have a valid login shell.
    %{_sbindir}/usermod -s /bin/bash %{tomcat_user} 2>/dev/null || :
    TOMCAT_SHELL="/bin/bash"
fi

# Add the tomcat group only if that group name doesn't already exist.
TOMCAT_GROUP_ID="`egrep '^%{tomcat_group}:' /etc/group | cut -d':' -f 3`" \
    2>/dev/null || :
if [ "$TOMCAT_GROUP_ID" == "" ]; then
    %{_sbindir}/groupadd -g %{tomcat_gid} %{tomcat_group} 2>/dev/null || :
    # If we get an error adding it with a specified group ID, add it
    # without specifying the group ID (otherwise we're in for errors).
    if [ $? == 1 ]; then
        %{_sbindir}/groupadd %{tomcat_group} 2>/dev/null || :
    fi
fi
# Get the gid of the tomcat group, whatever it ended up being.
TOMCAT_GROUP_ID="`egrep '^%{tomcat_group}:' /etc/group | cut -d':' -f 3`" \
    2>/dev/null || :

# Add the tomcat user if it doesn't already exist.
id %{tomcat_user} &>/dev/null
if [ $? == 1 ]; then
    %{_sbindir}/useradd -c "Tomcat JVM user." -g $TOMCAT_GROUP_ID \
        -s $TOMCAT_SHELL -r -M -d $RPM_INSTALL_PREFIX/temp \
        -u %{tomcat_uid} %{tomcat_user} 2>/dev/null || :
    # Try to lock the user's password.
    passwd -l %{tomcat_user} &>/dev/null || :
else
    # Since the user already existed, we probably shouldn't change it.
    # But, in the case where Tomcat won't run if we don't, we will.
    if [ TOMCAT_SHELL == "/bin/bash" ]; then
        usermod -s $TOMCAT_SHELL %{tomcat_user} || :
    fi

    TOMCAT_USER_DIR="`echo ~tomcat`"
	if [ "$TOMCAT_USER_DIR" == "/dev/null" ]; then
        usermod -d $RPM_INSTALL_PREFIX/temp tomcat
    fi
fi

%post
if [ "$SERVICE_NAME" == "" ]; then
    SERVICE_NAME="%{jvm_id}"
    if [ "$JVM_ID_SUFFIX" != "" ]; then
        SERVICE_NAME="%{jvm_id}-$JVM_ID_SUFFIX"
    fi
fi

# Symlink the init script into %{_sysconfdir}/init.d
rm -f %{_sysconfdir}/init.d/$SERVICE_NAME
ln -s $RPM_INSTALL_PREFIX/bin/%{package_name} \
    %{_sysconfdir}/init.d/$SERVICE_NAME

# Install the logrotate.d config fragment(s).
#install -d -m 755 %{_sysconfdir}/logrotate.d
#if [ -f "%{_sysconfdir}/logrotate.d/$SERVICE_NAME" ]; then
#    rm -f %{_sysconfdir}/logrotate.d/$SERVICE_NAME || :
#fi
#rm -f %{_sysconfdir}/logrotate.d/$SERVICE_NAME.rpmsave || :
#install -m 644 $RPM_INSTALL_PREFIX/conf/%{package_name}.logrotate \
#    %{_sysconfdir}/logrotate.d/$SERVICE_NAME

# Replace tokens with values in the scripts & conf files.
for i in $RPM_INSTALL_PREFIX/bin/%{package_name} \
         $RPM_INSTALL_PREFIX/conf/%{package_name}-env.sh
do
    perl -pi -e "s|\@PKG_NAME\@|%{package_name}|g;" $i
    perl -pi -e "s|\@TOMCAT_USER\@|%{tomcat_user}|g;" $i
    perl -pi -e "s|\@PKG_ROOT\@|$RPM_INSTALL_PREFIX|g;" $i
    perl -pi -e "s|\@TOMCAT_DIR\@|$RPM_INSTALL_PREFIX|g;" $i
    perl -pi -e "s|\@JVM_ID\@|$SERVICE_NAME|g;" $i
done

# Add the service via chkconfig.
if [ -x /sbin/chkconfig ]; then
    # Tell the init system about Tomcat's init script, and make sure
    # it starts at boot time.
    /sbin/chkconfig --add $SERVICE_NAME || :

    # Turn the service on in chkconfig, but only in production.
    if [ ! $DEV ]; then
        /sbin/chkconfig --level 2345 $SERVICE_NAME on || :
    else
        /sbin/chkconfig --level 2345 $SERVICE_NAME off || :
    fi
fi

# Create the /var/log/$SERVICE_NAME directory.  The logs will
# actually live there, and $CATALINA_BASE/logs will be a symlink to it.
# This is so the logs stay contained in the /var partition, if there is one.
install -d -m 755 -o %{tomcat_user} -g %{tomcat_group} /var/log/$SERVICE_NAME

# Symlink $CATALINA_BASE/logs to /var/log/$SERVICE_NAME.
# If it's already there, we'll get rid of it and make a new symlink.
if [ -h "$RPM_INSTALL_PREFIX/logs" ]; then
    # It's a symlink, so just remove it.
    rm -f $RPM_INSTALL_PREFIX/logs
fi
# If it's still there, and it's a directory, see if we can rmdir it.
if [ -d "$RPM_INSTALL_PREFIX/logs" ]; then
    rmdir $RPM_INSTALL_PREFIX/logs >/dev/null 2>&1 || :
fi
if [ -e "$RPM_INSTALL_PREFIX/logs" ]; then
    # It's probably either a file or a dir, so we'll move it.
    mv $RPM_INSTALL_PREFIX/logs $RPM_INSTALL_PREFIX/logs.rpmsave || :
fi
ln -s /var/log/$SERVICE_NAME $RPM_INSTALL_PREFIX/logs || :

# Always clean out the Tomcat $CATALINA_BASE/work dir on upgrade/removal.
rm -rf $RPM_INSTALL_PREFIX/work/* || :

%preun
if [ "$SERVICE_NAME" == "" ]; then
    SERVICE_NAME="%{package_name}"
    if [ "$JVM_ID_SUFFIX" != "" ]; then
        SERVICE_NAME="%{package_name}-$JVM_ID_SUFFIX"
    fi
fi

# Always clean up the Tomcat CATALINA_BASE/work dir on upgrade/removal.
rm -rf $RPM_INSTALL_PREFIX/work/*

if [ $1 = 0 ]; then
    # We're removing (rpm -e) the package.

    # Make sure the server is stopped.
    %{_sysconfdir}/init.d/$SERVICE_NAME stop >/dev/null 2>&1

    # If the init script exists, remove it from chkconfig.
    if [ -f %{_sysconfdir}/init.d/$SERVICE_NAME -a -x /sbin/chkconfig ]; then
        /sbin/chkconfig --del $SERVICE_NAME || :
    fi
fi

# We do not remove the Tomcat user since it may still own a lot of files.
# For instance, files in the logs and temp dirs.

%postun
if [ "$SERVICE_NAME" == "" ]; then
    SERVICE_NAME="%{jvm_id}"
    if [ "$JVM_ID_SUFFIX" != "" ]; then
        SERVICE_NAME="%{jvm_id}-$JVM_ID_SUFFIX"
    fi
fi

if [ $1 = 0 ]; then
    # We're uninstalling (rpm -e) the package.

    # Remove the init script.
    rm -f %{_sysconfdir}/init.d/$SERVICE_NAME || :

    # Remove the log dir if we created one, and if it's still empty.
    rmdir /var/log/$SERVICE_NAME >/dev/null 2>&1 || :
fi

%files -f .server.txt
# Default file ownership and group for the files/dirs in this package.
%defattr(-,%{tomcat_user},%{tomcat_group},-)

# Exclusions.
# This tells RPM not to think the main package owns these files and/or dirs.
%exclude %dir /opt

# Config files.
# Declare a file as "%config(noreplace)" if you never want an RPM install
# or upgrade to overwrite an already deployed copy of the file.
%config %{prefix}/conf/*
