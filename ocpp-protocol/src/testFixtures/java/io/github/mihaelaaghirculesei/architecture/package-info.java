/**
 * Architecture rules shared by every module. Outside the module packages on purpose: ArchUnit does
 * not treat test fixtures as tests, so a module importing its own packages would check these too.
 */
@NullMarked
package io.github.mihaelaaghirculesei.architecture;

import org.jspecify.annotations.NullMarked;
