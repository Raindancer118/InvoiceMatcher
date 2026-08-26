/// <reference types="astro/client" />

declare namespace App {
  interface Locals {
    /** Von der Middleware gesetzt; auf geschuetzten Seiten immer vorhanden. */
    sitzung: import('./lib/session').Sitzung;
  }
}
