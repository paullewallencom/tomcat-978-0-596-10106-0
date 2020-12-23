/*      $OpenBSD: chroot.c,v 1.7 2002/10/29 23:12:06 millert Exp $        */
/*      $NetBSD: chroot.c,v 1.11 2001/04/06 02:34:04 lukem Exp $        */

/*
 * Copyright (c) 1988, 1993
 *      The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *      This product includes software developed by the University of
 *      California, Berkeley and its contributors.
 * 4. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

/*
 * jbchroot.c
 * OpenBSD's chroot command for Linux and Solaris, ported by Jason Brittain.
 */
#ifndef lint
static const char copyright[] =
"@(#) Copyright (c) 1988, 1993\n\
        The Regents of the University of California.  All rights reserved.\n";
#endif /* not lint */

#ifndef lint
#if 0
static const char sccsid[] = "@(#)chroot.c      8.1 (Berkeley) 6/9/93";
#else
static const char rcsid[] = "$OpenBSD: chroot.c,v 1.7 2002/10/29 23:12:06 millert Exp $";
#endif
#endif /* not lint */

#include <ctype.h>
#include <errno.h>
#include <grp.h>
#include <limits.h>
#include <pwd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int             main(int, char **);
void            usage(char *);
static char*    getToken(char**, const char*);

int
main(int argc, char **argv)
{
  struct group        *gp;
  struct passwd        *pw;
  const char        *shell;
  char                *fulluser, *user, *group, *grouplist, *endp, *p;
  gid_t                gid, gidlist[NGROUPS_MAX];
  uid_t                uid;
  int                ch, gids;
  unsigned long        ul;
  char               *myname;

  myname = argv[0];

  gid = 0;
  uid = 0;
  gids = 0;
  user = fulluser = group = grouplist = NULL;
  while ((ch = getopt(argc, argv, "G:g:U:u:")) != -1) {
    switch(ch) {
    case 'U':
      fulluser = optarg;
      if (*fulluser == '\0')
	usage(myname);
      break;
    case 'u':
      user = optarg;
      if (*user == '\0')
	usage(myname);
      break;
    case 'g':
      group = optarg;
      if (*group == '\0')
	usage(myname);
      break;
    case 'G':
      grouplist = optarg;
      if (*grouplist == '\0')
	usage(myname);
      break;
    case '?':
    default:
      usage(myname);
    }
  }
  argc -= optind;
  argv += optind;

  if (argc < 1)
    usage(myname);
  if (fulluser && (user || group || grouplist)) {
    fprintf(stderr,
      "%s: The -U option may not be specified with any other option\n",
      myname);
    exit(-1);
  }

  if (group != NULL) {
    if ((gp = getgrnam(group)) != NULL)
      gid = gp->gr_gid;
    else if (isdigit((unsigned char)*group)) {
      errno = 0;
      ul = strtoul(group, &endp, 10);
      if (*endp != '\0' || (ul == ULONG_MAX && errno == ERANGE)) {
	fprintf(stderr, "%s: Invalid group ID `%s'\n", myname, group);
        exit(-1);
      }
      gid = (gid_t)ul;
    }
    else {
      fprintf(stderr, "%s: No such group `%s'\n", myname, group);
      exit(-1);
    }
    if (grouplist != NULL)
      gidlist[gids++] = gid;
    if (setgid(gid) != 0) {
      fprintf(stderr, "%s: setgid", myname);
      exit(-1);
    }
  }

  while ((p = getToken(&grouplist, ",")) != NULL && gids < NGROUPS_MAX) {
    if (*p == '\0')
      continue;

    if ((gp = getgrnam(p)) != NULL)
      gidlist[gids] = gp->gr_gid;
    else if (isdigit((unsigned char)*p)) {
      errno = 0;
      ul = strtoul(p, &endp, 10);
      if (*endp != '\0' || (ul == ULONG_MAX && errno == ERANGE)) {
	fprintf(stderr, "%s: Invalid group ID `%s'\n", myname, p);
        exit(-1);
      }
      gidlist[gids] = (gid_t)ul;
    }
    else {
      fprintf(stderr, "%s: No such group `%s'\n", myname, p);
      exit(-1);
    }
    /*
     * Ignore primary group if specified; we already added it above.
     */
    if (group == NULL || gidlist[gids] != gid)
      gids++;
  }
  if (p != NULL && gids == NGROUPS_MAX) {
    fprintf(stderr, "%s: Too many supplementary groups provided\n", myname);
    exit(-1);
  }
  if (gids && setgroups(gids, gidlist) != 0) {
    fprintf(stderr, "%s: setgroups", myname);
    exit(-1);
  }

  if (user != NULL) {
    if ((pw = getpwnam(user)) != NULL)
      uid = pw->pw_uid;
    else if (isdigit((unsigned char)*user)) {
      errno = 0;
      ul = strtoul(user, &endp, 10);
      if (*endp != '\0' || (ul == ULONG_MAX && errno == ERANGE)) {
	fprintf(stderr, "%s: Invalid user ID `%s'\n", myname, user);
        exit(-1);
      }
      uid = (uid_t)ul;
    }
    else {
      fprintf(stderr, "%s: No such user `%s'\n", myname, user);
      exit(-1);
    }
  }

  if (fulluser != NULL) {
    if ((pw = getpwnam(fulluser)) == NULL) {
      fprintf(stderr, "%s: No such user `%s'\n", myname, fulluser);
      exit(-1);
    }
    uid = pw->pw_uid;
    gid = pw->pw_gid;
    if (setgid(gid) != 0) {
      fprintf(stderr, "%s: setgid\n", myname);
      exit(-1);
    }
    if (initgroups(fulluser, gid) == -1) {
      fprintf(stderr, "%s: initgroups\n", myname);
      exit(-1);
    }
  }

  if (chroot(argv[0]) != 0 || chdir("/") != 0) {
    fprintf(stderr, "%s: %s\n", myname, argv[0]);
    exit(-1);
  }

  if ((user || fulluser) && setuid(uid) != 0) {
    fprintf(stderr, "%s: setuid\n", myname);
    exit(-1);
  }

  if (argv[1]) {
    execvp(argv[1], &argv[1]);
    fprintf(stderr, "%s: %s\n", myname, argv[1]);
    exit(-1);
  }

  if ((shell = getenv("SHELL")) == NULL)
    shell = "/bin/sh";
  execlp(shell, shell, "-i", (char *)NULL);
  fprintf(stderr, "%s, %s\n", myname, shell);
  /* NOTREACHED */
}

void
usage(char *myname)
{
  (void)fprintf(stderr, "usage: %s [-g group] [-G group,group,...] "
		"[-u user] [-U user] newroot [command]\n", myname);
  exit(1);
}

/* This is a replacement for strsep which is missing on Solaris. */
static char* getToken(char** str, const char* delims)
{
  char* token;

  if (*str==NULL) {
    /* No more tokens */
    return NULL;
  }

  token=*str;
  while (**str!='\0') {
    if (strchr(delims,**str)!=NULL) {
      **str='\0';
      (*str)++;
      return token;
    }
    (*str)++;
  }
  /* There is no other token */
  *str=NULL;
  return token;
}
