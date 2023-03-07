import React from "react";
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import {Link} from "react-router-dom";
import {NavDropdown} from "react-bootstrap";
import {DepthUnit} from "../App";
import '../App.css';

type NavLinkProps = {
    path: string,
    label: string,
}

function NavLink(props: NavLinkProps) {
    return (
        <li className="nav-item"><Link to={props.path} className="nav-link">{props.label}</Link></li>
    )
}

type NavBarProps = {
    depths: DepthUnit,
    updater: (depths: DepthUnit) => void,
}

export function NavBar(props: NavBarProps) {
    return (
        <Navbar className="Wrap" bg="secondary" expand="lg" variant="dark">
            <Container>
                <Navbar.Brand>Njord</Navbar.Brand>
                <Navbar.Toggle aria-controls="basic-navbar-nav"/>
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto">
                        <NavLink path="/" label="About"/>
                        <NavLink path="/enc" label="ENC"/>
                        <NavLink path="/control/charts" label="Control Panel"/>
                        <NavDropdown title={"Depths: " + props.depths.toUpperCase()}
                                     id="basic-nav-dropdown">
                            <NavDropdown.Item onClick={() => {
                                props.updater(DepthUnit.meters);
                            }}>Meters</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.updater(DepthUnit.fathoms);
                            }}>Fathoms & Feet</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.updater(DepthUnit.feet);
                            }}>Feet</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}
